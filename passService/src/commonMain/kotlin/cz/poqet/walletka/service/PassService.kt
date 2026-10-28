package cz.poqet.walletka.service

import cz.poqet.walletka.api.WalletkaApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** An error whose message is safe to return to clients. */
class ServiceException(message: String) : Exception(message)

/** Where uploaded banners live (R2 in the Worker). */
interface BannerStore {
    suspend fun put(key: String, png: ByteArray)
}

class PassServiceConfig(
    val passTypeIdentifier: String,
    val teamIdentifier: String,
    /** PEM of the Pass Type ID certificate. */
    val certificatePem: String,
    /** PEM of Apple's WWDR intermediate certificate. */
    val wwdrCertificatePem: String,
)

class SignedPass(val pkpass: ByteArray, val serialNumber: String)

class Banner(val id: String, val url: String)

/** Business logic of the pass service, independent of the Worker runtime. */
class PassService(
    private val config: PassServiceConfig,
    private val crypto: PassCrypto,
    private val bannerStore: BannerStore,
    private val now: () -> Long,
) {

    private val cms by lazy {
        val signer = Pem.certificates(config.certificatePem).firstOrNull()
            ?: throw ServiceException("Pass certificate is not configured")
        val wwdr = Pem.certificates(config.wwdrCertificatePem)
        if (wwdr.isEmpty()) throw ServiceException("WWDR certificate is not configured")
        Cms(crypto, signer, wwdr)
    }

    /**
     * Validates the pass files, stamps the issuer identifiers into `pass.json`, and returns the signed `.pkpass`
     * (files + `manifest.json` + detached `signature`).
     */
    @OptIn(ExperimentalUuidApi::class)
    suspend fun signApplePass(files: List<Pair<String, ByteArray>>): SignedPass {
        validate(files)
        val original = files.first { it.first == PASS_JSON }.second
        val pass = runCatching { Json.parseToJsonElement(original.decodeToString()).jsonObject }
            .getOrElse { throw ServiceException("pass.json is not a JSON object") }
        val serialNumber = (pass["serialNumber"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            ?: Uuid.random().toString()
        val stamped = JsonObject(
            pass + buildJsonObject {
                put("formatVersion", 1)
                put("passTypeIdentifier", config.passTypeIdentifier)
                put("teamIdentifier", config.teamIdentifier)
                put("serialNumber", serialNumber)
            },
        ).toString().encodeToByteArray()

        val bundle = files.map { (name, bytes) -> name to if (name == PASS_JSON) stamped else bytes }
        val manifest = buildJsonObject {
            for ((name, bytes) in bundle) put(name, crypto.sha1(bytes).toHex())
        }.toString().encodeToByteArray()
        val signature = cms.signDetached(manifest, signingTimeEpochSeconds = now())

        val archive = Zip.write(bundle + listOf(MANIFEST to manifest, SIGNATURE to signature))
        return SignedPass(archive, serialNumber)
    }

    /** Stores [png] under a content-addressed key and returns its public URL below [baseUrl]. */
    suspend fun uploadBanner(png: ByteArray, baseUrl: String): Banner {
        if (png.size > MAX_BANNER_BYTES) throw ServiceException("Banner is larger than ${MAX_BANNER_BYTES / 1024} kB")
        if (!png.isPng()) throw ServiceException("Banner must be a PNG image")
        val id = crypto.sha256(png).toHex().take(32)
        bannerStore.put(bannerKey(id), png)
        return Banner(id, baseUrl.trimEnd('/') + WalletkaApi.BANNERS_PATH + "$id.png")
    }

    private fun validate(files: List<Pair<String, ByteArray>>) {
        val names = files.map { it.first }
        if (names.toSet().size != names.size) throw ServiceException("Duplicate file names")
        names.firstOrNull { !allowedFile.matches(it) }?.let { throw ServiceException("File not allowed in a pass: $it") }
        if (PASS_JSON !in names) throw ServiceException("pass.json is missing")
        if (names.none { it.startsWith("icon") }) throw ServiceException("icon.png is missing")
        if (files.sumOf { it.second.size } > MAX_PASS_BYTES) throw ServiceException("Pass files exceed ${MAX_PASS_BYTES / 1024} kB")
        files.firstOrNull { (name, bytes) -> name.endsWith(".png") && !bytes.isPng() }
            ?.let { throw ServiceException("${it.first} is not a PNG image") }
    }

    companion object {
        const val PASS_JSON = "pass.json"
        const val MANIFEST = "manifest.json"
        const val SIGNATURE = "signature"
        const val MAX_PASS_BYTES = 3 * 1024 * 1024
        const val MAX_BANNER_BYTES = 2 * 1024 * 1024

        private val allowedFile = Regex("""pass\.json|(icon|logo|strip|thumbnail|background|footer)(@[23]x)?\.png""")
        private val bannerIdRegex = Regex("[0-9a-f]{32}")

        fun bannerKey(id: String) = "banners/$id.png"

        /** Maps a public banner path (`/banners/<id>.png`) to its storage key, or `null` if it is not one. */
        fun bannerKeyForPath(path: String): String? {
            val file = path.removePrefix(WalletkaApi.BANNERS_PATH).takeIf { it != path } ?: return null
            val id = file.removeSuffix(".png").takeIf { it != file && bannerIdRegex.matches(it) } ?: return null
            return bannerKey(id)
        }

        private val PNG_MAGIC = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        private fun ByteArray.isPng() = size > 8 && copyOfRange(0, 8).contentEquals(PNG_MAGIC)

        private fun ByteArray.toHex() = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }
}
