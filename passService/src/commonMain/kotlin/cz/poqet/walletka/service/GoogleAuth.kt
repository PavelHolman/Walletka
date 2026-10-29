package cz.poqet.walletka.service

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64

/** Minimal HTTP seam, so the Google Wallet code runs against `fetch` in the Worker and against fakes in tests. */
fun interface HttpTransport {
    suspend fun send(request: HttpRequest): HttpResponse
}

class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
)

class HttpResponse(val status: Int, val body: String) {
    val isSuccess: Boolean get() = status in 200..299
}

/** The fields of a Google Cloud service account key file (`GOOGLE_SERVICE_ACCOUNT_JSON`) the service uses. */
class GoogleServiceAccount(val clientEmail: String, val privateKeyPem: String, val tokenUri: String) {
    companion object {
        const val DEFAULT_TOKEN_URI = "https://oauth2.googleapis.com/token"

        /** Parses a service account key file; `null` when [json] isn't one. */
        fun parse(json: String): GoogleServiceAccount? = runCatching {
            val obj = Json.parseToJsonElement(json).jsonObject
            GoogleServiceAccount(
                clientEmail = obj.getValue("client_email").jsonPrimitive.content,
                privateKeyPem = obj.getValue("private_key").jsonPrimitive.content,
                tokenUri = obj["token_uri"]?.jsonPrimitive?.content ?: DEFAULT_TOKEN_URI,
            )
        }.getOrNull()?.takeIf { it.clientEmail.isNotBlank() && "PRIVATE KEY" in it.privateKeyPem }
    }
}

/** Compact JWS with RS256, as Google's OAuth token endpoint and "Save to Google Wallet" links expect. */
object Jwt {
    private val base64Url = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    private val header = buildJsonObject { put("alg", "RS256"); put("typ", "JWT") }

    suspend fun signRs256(claims: JsonObject, sign: suspend (ByteArray) -> ByteArray): String {
        val signingInput = encode(header.toString().encodeToByteArray()) + "." + encode(claims.toString().encodeToByteArray())
        return signingInput + "." + encode(sign(signingInput.encodeToByteArray()))
    }

    fun encode(bytes: ByteArray): String = base64Url.encode(bytes)

    fun decode(part: String): ByteArray = base64Url.decode(part)
}

/**
 * OAuth access tokens for the service account (two-legged JWT bearer grant). A token is reused until a minute
 * before it expires; the Worker keeps one instance per isolate, so it lives in memory.
 */
class GoogleAccessTokens(
    private val account: GoogleServiceAccount,
    private val crypto: PassCrypto,
    private val http: HttpTransport,
    private val now: () -> Long,
    private val scope: String = WALLET_SCOPE,
) {
    private val mutex = Mutex()
    private var token: String? = null
    private var expiresAt = 0L

    suspend fun get(): String = mutex.withLock {
        token?.takeIf { now() < expiresAt - REFRESH_MARGIN_SECONDS } ?: fetch()
    }

    /** Drops the cached token, e.g. after the API rejected it. */
    suspend fun invalidate() = mutex.withLock { token = null }

    private suspend fun fetch(): String {
        val issuedAt = now()
        val assertion = Jwt.signRs256(
            buildJsonObject {
                put("iss", account.clientEmail)
                put("scope", scope)
                put("aud", account.tokenUri)
                put("iat", issuedAt)
                put("exp", issuedAt + 3600)
            },
            crypto::signRsaSha256,
        )
        val response = http.send(
            HttpRequest(
                method = "POST",
                url = account.tokenUri,
                headers = mapOf("content-type" to "application/x-www-form-urlencoded"),
                body = "grant_type=${urlEncode(JWT_BEARER_GRANT)}&assertion=$assertion",
            ),
        )
        if (!response.isSuccess) throw ServiceException("Google sign-in failed (${response.status})")
        val json = runCatching { Json.parseToJsonElement(response.body).jsonObject }.getOrNull()
        val accessToken = json?.get("access_token")?.jsonPrimitive?.content
            ?: throw ServiceException("Google sign-in returned no access token")
        val lifetime = json["expires_in"]?.jsonPrimitive?.long ?: 3600
        token = accessToken
        expiresAt = issuedAt + lifetime
        return accessToken
    }

    companion object {
        const val WALLET_SCOPE = "https://www.googleapis.com/auth/wallet_object.issuer"
        const val JWT_BEARER_GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer"
        private const val REFRESH_MARGIN_SECONDS = 60

        private fun urlEncode(value: String) = value.encodeToByteArray().joinToString("") { byte ->
            val code = byte.toInt() and 0xFF
            val c = code.toChar()
            if (c in 'A'..'Z' || c in 'a'..'z' || c in '0'..'9' || c in "-._~") c.toString()
            else "%" + code.toString(16).uppercase().padStart(2, '0')
        }
    }
}
