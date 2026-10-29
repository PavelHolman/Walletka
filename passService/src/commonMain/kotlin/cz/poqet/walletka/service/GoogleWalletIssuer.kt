package cz.poqet.walletka.service

import cz.poqet.walletka.api.WalletkaApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A card as the app describes it; [GoogleWalletIssuer] validates it and turns it into a generic object. */
class GooglePass(
    val id: String,
    val cardTitle: String,
    val header: String,
    val hexBackgroundColor: String,
    val barcodeType: String,
    val barcodeValue: String,
    val heroImageUrl: String?,
    /** A logo uploaded like the banner; Google Wallet shows it in a circle. */
    val logoUrl: String? = null,
    /** Line under the header, e.g. the member name. */
    val subheader: String? = null,
    /** Last day the card is valid, ISO 8601 (`2027-05-31`); the pass ends at 23:59:59 UTC that day. */
    val expiryDate: String? = null,
    /** Back-of-pass details. */
    val textModules: List<GoogleTextModule> = emptyList(),
)

/** A Google Wallet text module: [header] over [body], identified by [id]. */
class GoogleTextModule(val id: String, val header: String, val body: String)

class GoogleSaveLink(val jwt: String, val saveUrl: String, val objectId: String)

/**
 * Issues Google Wallet generic passes through the Google Wallet REST API with the issuer's service account.
 *
 * Objects are created (or replaced) server side first, so the "Save to Google Wallet" JWT only references them,
 * and later edits are pushed with the same object ID. All passes share one generic class, created on first use;
 * it has the same ID as the class the app's on-device fallback uses.
 */
class GoogleWalletIssuer(
    private val issuerId: String,
    private val account: GoogleServiceAccount,
    private val crypto: PassCrypto,
    private val http: HttpTransport,
    private val now: () -> Long,
) {
    private val tokens = GoogleAccessTokens(account, crypto, http, now)
    private var classEnsured = false

    val classId: String get() = "$issuerId.$CLASS_SUFFIX"

    fun objectId(passId: String) = "$issuerId.$passId"

    /** Creates or replaces the object and returns a signed link that saves it to the user's Google Wallet. */
    suspend fun save(pass: GooglePass, baseUrl: String): GoogleSaveLink {
        val body = genericObject(validate(pass, baseUrl))
        ensureClass()
        val created = api("POST", "genericObject", body)
        when {
            created.isSuccess -> Unit
            created.status == 409 -> api("PUT", "genericObject/${objectId(pass.id)}", body).orThrow()
            else -> created.orThrow()
        }
        val jwt = Jwt.signRs256(
            buildJsonObject {
                put("iss", account.clientEmail)
                put("aud", "google")
                put("typ", "savetowallet")
                put("iat", now())
                putJsonArray("origins") {}
                putJsonObject("payload") {
                    putJsonArray("genericObjects") {
                        addJsonObject { put("id", objectId(pass.id)); put("classId", classId) }
                    }
                }
            },
            crypto::signRsaSha256,
        )
        return GoogleSaveLink(jwt, SAVE_URL + jwt, objectId(pass.id))
    }

    /** Replaces an existing object; `false` when there is none (the card was never saved through the service). */
    suspend fun update(pass: GooglePass, baseUrl: String): Boolean {
        val body = genericObject(validate(pass, baseUrl))
        val response = api("PUT", "genericObject/${objectId(pass.id)}", body)
        if (response.status == 404) return false
        response.orThrow()
        return true
    }

    /**
     * Objects can't be deleted through the API, so the pass is expired instead: Google Wallet moves it out of the
     * user's active passes. `false` when the object doesn't exist.
     */
    suspend fun expire(passId: String): Boolean {
        validateId(passId)
        val response = api("PATCH", "genericObject/${objectId(passId)}", buildJsonObject { put("state", "EXPIRED") })
        if (response.status == 404) return false
        response.orThrow()
        return true
    }

    private suspend fun ensureClass() {
        if (classEnsured) return
        val existing = api("GET", "genericClass/$classId", null)
        if (existing.status == 404) {
            val created = api("POST", "genericClass", buildJsonObject { put("id", classId) })
            if (created.status != 409) created.orThrow()
        } else {
            existing.orThrow()
        }
        classEnsured = true
    }

    /** Calls the Wallet Objects API; a rejected (e.g. revoked) token is refreshed once. */
    private suspend fun api(method: String, path: String, body: JsonObject?): HttpResponse {
        suspend fun send() = http.send(
            HttpRequest(
                method = method,
                url = API_BASE + path,
                headers = buildMap {
                    put("authorization", "Bearer ${tokens.get()}")
                    if (body != null) put("content-type", "application/json")
                },
                body = body?.toString(),
            ),
        )
        val response = send()
        if (response.status != 401) return response
        tokens.invalidate()
        return send()
    }

    private fun HttpResponse.orThrow() {
        if (isSuccess) return
        val message = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        throw ServiceException("Google Wallet API responded $status" + (message?.let { ": $it" } ?: ""))
    }

    private fun genericObject(pass: GooglePass) = buildJsonObject {
        put("id", objectId(pass.id))
        put("classId", classId)
        put("genericType", "GENERIC_TYPE_UNSPECIFIED")
        put("state", "ACTIVE")
        put("hexBackgroundColor", pass.hexBackgroundColor)
        putJsonObject("cardTitle") { localized(pass.cardTitle) }
        putJsonObject("header") { localized(pass.header) }
        pass.heroImageUrl?.let { url ->
            putJsonObject("heroImage") {
                putJsonObject("sourceUri") { put("uri", url) }
                putJsonObject("contentDescription") { localized(pass.header) }
            }
        }
        pass.logoUrl?.let { url ->
            putJsonObject("logo") {
                putJsonObject("sourceUri") { put("uri", url) }
                putJsonObject("contentDescription") { localized(pass.header) }
            }
        }
        pass.subheader?.let { putJsonObject("subheader") { localized(it) } }
        pass.expiryDate?.let { date ->
            putJsonObject("validTimeInterval") {
                putJsonObject("end") { put("date", "${date}T23:59:59Z") }
            }
        }
        if (pass.textModules.isNotEmpty()) {
            putJsonArray("textModulesData") {
                pass.textModules.forEach { module ->
                    addJsonObject {
                        put("id", module.id)
                        put("header", module.header)
                        put("body", module.body)
                    }
                }
            }
        }
        putJsonObject("barcode") {
            put("type", pass.barcodeType)
            put("value", pass.barcodeValue)
            put("alternateText", pass.barcodeValue.take(MAX_ALTERNATE_TEXT))
        }
    }

    private fun JsonObjectBuilder.localized(value: String) {
        putJsonObject("defaultValue") {
            put("language", "en")
            put("value", value)
        }
    }

    companion object {
        const val API_BASE = "https://walletobjects.googleapis.com/walletobjects/v1/"
        const val SAVE_URL = "https://pay.google.com/gp/v/save/"
        const val CLASS_SUFFIX = "walletka_generic"
        const val MAX_TEXT = 100
        const val MAX_BARCODE_VALUE = 2048
        const val MAX_TEXT_MODULES = 10
        const val MAX_TEXT_MODULE_BODY = 1000
        private const val MAX_ALTERNATE_TEXT = 64

        private val idRegex = Regex("[A-Za-z0-9._-]{1,64}")
        private val issuerIdRegex = Regex("[0-9]{1,32}")
        private val colorRegex = Regex("#[0-9a-fA-F]{6}")
        private val dateRegex = Regex("[0-9]{4}-(0[1-9]|1[0-2])-(0[1-9]|[12][0-9]|3[01])")
        private val moduleIdRegex = Regex("[A-Za-z0-9_]{1,32}")

        /** Google Wallet `BarcodeType`s the app produces (see the app's `WalletSymbology`). */
        val barcodeTypes = setOf(
            "QR_CODE", "AZTEC", "PDF_417", "DATA_MATRIX", "CODE_128", "CODE_39", "CODABAR",
            "EAN_13", "EAN_8", "UPC_A", "ITF_14",
        )

        fun isValidIssuerId(value: String) = issuerIdRegex.matches(value)

        private fun validateId(id: String) {
            if (!idRegex.matches(id)) throw ServiceException("Pass id must be 1-64 characters of A-Z, a-z, 0-9, '.', '_' or '-'")
        }

        /** Checks [pass] and normalises its texts; the hero image and logo must be banners hosted by this service. */
        fun validate(pass: GooglePass, baseUrl: String): GooglePass {
            validateId(pass.id)
            val cardTitle = pass.cardTitle.trim()
            val header = pass.header.trim()
            for ((field, value) in listOf("cardTitle" to cardTitle, "header" to header)) {
                if (value.isEmpty()) throw ServiceException("$field must not be empty")
                if (value.length > MAX_TEXT) throw ServiceException("$field is longer than $MAX_TEXT characters")
            }
            if (!colorRegex.matches(pass.hexBackgroundColor)) throw ServiceException("hexBackgroundColor must be #rrggbb")
            if (pass.barcodeType !in barcodeTypes) throw ServiceException("Unsupported barcode type: ${pass.barcodeType}")
            if (pass.barcodeValue.isEmpty() || pass.barcodeValue.length > MAX_BARCODE_VALUE) {
                throw ServiceException("barcodeValue must be 1-$MAX_BARCODE_VALUE characters")
            }
            pass.heroImageUrl?.let { validateHostedImage("heroImageUrl", it, baseUrl) }
            pass.logoUrl?.let { validateHostedImage("logoUrl", it, baseUrl) }
            val subheader = pass.subheader?.trim()?.ifEmpty { null }
            if (subheader != null && subheader.length > MAX_TEXT) throw ServiceException("subheader is longer than $MAX_TEXT characters")
            val expiryDate = pass.expiryDate?.trim()?.ifEmpty { null }
            if (expiryDate != null && !dateRegex.matches(expiryDate)) throw ServiceException("expiryDate must be YYYY-MM-DD")
            if (pass.textModules.size > MAX_TEXT_MODULES) throw ServiceException("At most $MAX_TEXT_MODULES textModules")
            val modules = pass.textModules.map { module ->
                if (!moduleIdRegex.matches(module.id)) throw ServiceException("textModules id must be 1-32 characters of A-Z, a-z, 0-9 or '_'")
                val moduleHeader = module.header.trim()
                val body = module.body.trim()
                if (moduleHeader.length > MAX_TEXT) throw ServiceException("textModules header is longer than $MAX_TEXT characters")
                if (body.isEmpty() || body.length > MAX_TEXT_MODULE_BODY) {
                    throw ServiceException("textModules body must be 1-$MAX_TEXT_MODULE_BODY characters")
                }
                GoogleTextModule(module.id, moduleHeader, body)
            }
            if (modules.map { it.id }.toSet().size != modules.size) throw ServiceException("textModules ids must be unique")
            return GooglePass(
                id = pass.id,
                cardTitle = cardTitle,
                header = header,
                hexBackgroundColor = pass.hexBackgroundColor.lowercase(),
                barcodeType = pass.barcodeType,
                barcodeValue = pass.barcodeValue,
                heroImageUrl = pass.heroImageUrl,
                logoUrl = pass.logoUrl,
                subheader = subheader,
                expiryDate = expiryDate,
                textModules = modules,
            )
        }

        /** Images must be banners uploaded to this service, so passes can't point Google at arbitrary URLs. */
        private fun validateHostedImage(field: String, url: String, baseUrl: String) {
            val bannerPrefix = baseUrl.trimEnd('/') + WalletkaApi.BANNERS_PATH
            val path = url.removePrefix(baseUrl.trimEnd('/'))
            if (!url.startsWith(bannerPrefix) || PassService.bannerKeyForPath(path) == null) {
                throw ServiceException("$field must be a banner uploaded to this service")
            }
        }
    }
}
