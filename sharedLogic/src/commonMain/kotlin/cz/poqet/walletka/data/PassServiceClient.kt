package cz.poqet.walletka.data

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.poqet.walletka.api.WalletkaApi
import cz.poqet.walletka.domain.WalletError
import cz.poqet.walletka.domain.WalletFailure
import io.ktor.client.HttpClient
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.io.encoding.Base64

/** GraphQL client of the Walletka pass service; the operations come from the shared `:serviceApi` contract. */
internal class PassServiceClient(
    private val config: WalletConfig,
    private val http: HttpClient = HttpClient(),
) {

    /** Returns the signed `.pkpass` for the given pass files. */
    suspend fun signApplePass(files: Map<String, ByteArray>): Either<WalletError, ByteArray> = either {
        val data = execute(WalletkaApi.SIGN_APPLE_PASS) {
            putJsonArray("files") {
                files.forEach { (name, bytes) ->
                    addJsonObject {
                        put("name", name)
                        put("base64", Base64.encode(bytes))
                    }
                }
            }
        }
        val pkpass = data["signApplePass"]?.jsonObject?.get("pkpassBase64")?.jsonPrimitive?.content
        Base64.decode(ensureNotNull(pkpass) { failed("Malformed signApplePass response") })
    }

    /** Uploads a PNG banner and returns its public URL. */
    suspend fun uploadBanner(png: ByteArray): Either<WalletError, String> = either {
        val data = execute(WalletkaApi.UPLOAD_BANNER) { put("pngBase64", Base64.encode(png)) }
        ensureNotNull(data["uploadBanner"]?.jsonObject?.get("url")?.jsonPrimitive?.content) {
            failed("Malformed uploadBanner response")
        }
    }

    /** What the deployed service is set up for. */
    suspend fun capabilities(): Either<WalletError, ServiceCapabilities> = either {
        val data = execute(WalletkaApi.CAPABILITIES) {}
        val capabilities = ensureNotNull(data["capabilities"] as? JsonObject) { failed("Malformed capabilities response") }
        ServiceCapabilities(
            appleWallet = capabilities["appleWallet"]?.jsonPrimitive?.booleanOrNull == true,
            googleWallet = capabilities["googleWallet"]?.jsonPrimitive?.booleanOrNull == true,
        )
    }

    /** Creates (or replaces) the Google Wallet object for [pass] and returns a signed "Save to Google Wallet" JWT. */
    suspend fun saveGooglePass(pass: JsonObject): Either<WalletError, String> = either {
        val data = execute(WalletkaApi.SAVE_GOOGLE_PASS) { put("pass", pass) }
        ensureNotNull(data["saveGooglePass"]?.jsonObject?.get("jwt")?.jsonPrimitive?.content) {
            failed("Malformed saveGooglePass response")
        }
    }

    /** Replaces the Google Wallet object; `NOT_IN_WALLET` when the service has no object with that ID. */
    suspend fun updateGooglePass(pass: JsonObject): Either<WalletError, Unit> = either {
        val data = execute(WalletkaApi.UPDATE_GOOGLE_PASS) { put("pass", pass) }
        val found = data["updateGooglePass"]?.jsonObject?.get("found")?.jsonPrimitive?.booleanOrNull
        ensureNotNull(found) { failed("Malformed updateGooglePass response") }
        ensure(found) { WalletError(WalletFailure.NOT_IN_WALLET) }
    }

    /** Expires the Google Wallet object; `false` when there was none. */
    suspend fun deleteGooglePass(id: String): Either<WalletError, Boolean> = either {
        val data = execute(WalletkaApi.DELETE_GOOGLE_PASS) { put("id", id) }
        ensureNotNull(data["deleteGooglePass"]?.jsonPrimitive?.booleanOrNull) { failed("Malformed deleteGooglePass response") }
    }

    private suspend fun Raise<WalletError>.execute(
        query: String,
        variables: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit,
    ): JsonObject {
        val baseUrl = ensureNotNull(config.serviceUrl?.takeIf { it.isNotBlank() }) {
            WalletError(WalletFailure.NOT_CONFIGURED, "Pass service URL missing")
        }
        val body = buildJsonObject {
            put("query", query)
            putJsonObject("variables", variables)
        }
        val response = Either.catch {
            http.post(baseUrl.trimEnd('/') + WalletkaApi.GRAPHQL_PATH) {
                contentType(ContentType.Application.Json)
                config.serviceApiKey?.takeIf { it.isNotBlank() }?.let { bearerAuth(it) }
                setBody(body.toString())
            }
        }.mapLeft { failed("Pass service unreachable: ${it.message}") }.bind()

        val text = response.bodyAsText()
        val json = Either.catch { Json.parseToJsonElement(text).jsonObject }
            .mapLeft { failed("Pass service responded ${response.status.value}") }
            .bind()
        val error = json["errors"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonPrimitive?.content
        ensure(error == null && response.status.isSuccess()) { failed(error ?: "Pass service responded ${response.status.value}") }
        return ensureNotNull(json["data"] as? JsonObject) { failed("Pass service returned no data") }
    }

    private fun failed(detail: String) = WalletError(WalletFailure.FAILED, detail)
}

/** What a pass service deployment supports (`capabilities` query). */
internal data class ServiceCapabilities(val appleWallet: Boolean, val googleWallet: Boolean)
