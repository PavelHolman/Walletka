package cz.poqet.walletka.service

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.util.zip.ZipInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class JvmPassCrypto(privateKeyPkcs8: ByteArray) : PassCrypto {
    private val key = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(privateKeyPkcs8))
    override suspend fun sha1(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-1").digest(data)
    override suspend fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)
    override suspend fun signRsaSha256(data: ByteArray): ByteArray =
        Signature.getInstance("SHA256withRSA").run { initSign(key); update(data); sign() }
}

private class MemoryBannerStore : BannerStore {
    val stored = mutableMapOf<String, ByteArray>()
    override suspend fun put(key: String, png: ByteArray) {
        stored[key] = png
    }
}

class PassServiceJvmTest {

    private fun resource(name: String) = checkNotNull(javaClass.getResource("/test-certs/$name")).readText()

    private val banners = MemoryBannerStore()
    private val service = PassService(
        config = PassServiceConfig(
            passTypeIdentifier = "pass.cz.poqet.walletka.test",
            teamIdentifier = "TESTTEAM01",
            certificatePem = resource("pass-cert.pem"),
            wwdrCertificatePem = resource("wwdr.pem"),
        ),
        crypto = JvmPassCrypto(Pem.privateKeyPkcs8(resource("pass-key.pem"))),
        bannerStore = banners,
        now = { 1_793_000_000 },
    )

    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(32) { it.toByte() }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> = buildMap {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            generateSequence { zip.nextEntry }.forEach { put(it.name, zip.readBytes()) }
        }
    }

    @Test
    fun signedPassIsAValidPkpass() = runTest {
        val pass = """{"description":"Coffee Club","passTypeIdentifier":"spoofed","storeCard":{}}""".encodeToByteArray()
        val signed = service.signApplePass(listOf("pass.json" to pass, "icon.png" to png, "strip@2x.png" to png))

        val files = unzip(signed.pkpass)
        assertEquals(setOf("pass.json", "icon.png", "strip@2x.png", "manifest.json", "signature"), files.keys)

        val passJson = Json.parseToJsonElement(files.getValue("pass.json").decodeToString()).jsonObject
        assertEquals("pass.cz.poqet.walletka.test", passJson["passTypeIdentifier"]!!.jsonPrimitive.content)
        assertEquals("TESTTEAM01", passJson["teamIdentifier"]!!.jsonPrimitive.content)
        assertEquals(signed.serialNumber, passJson["serialNumber"]!!.jsonPrimitive.content)

        val manifest = Json.parseToJsonElement(files.getValue("manifest.json").decodeToString()).jsonObject
        for (name in listOf("pass.json", "icon.png", "strip@2x.png")) {
            val sha1 = MessageDigest.getInstance("SHA-1").digest(files.getValue(name)).joinToString("") { "%02x".format(it) }
            assertEquals(sha1, manifest[name]!!.jsonPrimitive.content, name)
        }

        verifyWithOpenSsl(files.getValue("manifest.json"), files.getValue("signature"))
    }

    /** Independent check of the hand-written CMS encoding: OpenSSL must accept the detached signature. */
    private fun verifyWithOpenSsl(manifest: ByteArray, signature: ByteArray) {
        val dir = Files.createTempDirectory("pkpass").toFile()
        val manifestFile = File(dir, "manifest.json").apply { writeBytes(manifest) }
        val signatureFile = File(dir, "signature").apply { writeBytes(signature) }
        val caFile = File(dir, "ca.pem").apply { writeText(resource("wwdr.pem")) }
        val process = ProcessBuilder(
            "openssl", "cms", "-verify", "-binary", "-inform", "DER",
            "-in", signatureFile.path, "-content", manifestFile.path, "-CAfile", caFile.path,
            "-purpose", "any", "-out", File(dir, "out").path,
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        assertEquals(0, process.waitFor(), "openssl cms -verify failed:\n$output")
        assertTrue("Verification successful" in output, output)
    }

    @Test
    fun acceptsLogoImages() = runTest {
        val logos = listOf("logo.png", "logo@2x.png", "logo@3x.png")
        val signed = service.signApplePass(listOf("pass.json" to "{}".encodeToByteArray(), "icon.png" to png) + logos.map { it to png })
        assertTrue(unzip(signed.pkpass).keys.containsAll(logos))
    }

    @Test
    fun rejectsUnexpectedFiles() = runTest {
        val error = kotlin.runCatching {
            service.signApplePass(listOf("pass.json" to "{}".encodeToByteArray(), "icon.png" to png, "../evil" to png))
        }.exceptionOrNull()
        assertTrue(error is ServiceException && "not allowed" in error.message.orEmpty(), "$error")
    }

    @Test
    fun bannersAreContentAddressed() = runTest {
        val banner = service.uploadBanner(png, baseUrl = "https://passes.example.com/")
        assertTrue(banner.url.startsWith("https://passes.example.com/banners/") && banner.url.endsWith(".png"))
        assertEquals(PassService.bannerKey(banner.id), PassService.bannerKeyForPath("/banners/${banner.id}.png"))
        assertEquals(png.toList(), banners.stored.getValue(PassService.bannerKey(banner.id)).toList())
        assertEquals(null, PassService.bannerKeyForPath("/banners/../secret.png"))
    }
}
