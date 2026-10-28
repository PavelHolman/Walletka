package cz.poqet.walletka.data

import cz.poqet.walletka.api.WalletkaApi
import cz.poqet.walletka.domain.WalletFailure
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals

class PassServiceClientTest {

    private val config = WalletConfig(serviceUrl = "https://passes.example.com/", serviceApiKey = "secret")
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun signsPassThroughGraphQl() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https://passes.example.com/graphql", request.url.toString())
            assertEquals("Bearer secret", request.headers[HttpHeaders.Authorization])
            val body = Json.parseToJsonElement(request.body.toByteArray().decodeToString()).jsonObject
            assertEquals(WalletkaApi.SIGN_APPLE_PASS, body["query"]!!.jsonPrimitive.content)
            val file = body["variables"]!!.jsonObject["files"]!!.jsonArray.single().jsonObject
            assertEquals("pass.json", file["name"]!!.jsonPrimitive.content)
            val pkpass = Base64.encode("PK".encodeToByteArray())
            respond("""{"data":{"signApplePass":{"pkpassBase64":"$pkpass","serialNumber":"1"}}}""", headers = jsonHeaders)
        }
        val result = PassServiceClient(config, HttpClient(engine)).signApplePass(mapOf("pass.json" to "{}".encodeToByteArray()))
        assertEquals("PK", result.getOrNull()?.decodeToString())
    }

    @Test
    fun surfacesGraphQlErrors() = runTest {
        val engine = MockEngine {
            respond("""{"errors":[{"message":"Banner must be a PNG image"}],"data":null}""", headers = jsonHeaders)
        }
        val error = PassServiceClient(config, HttpClient(engine)).uploadBanner(ByteArray(4)).leftOrNull()
        assertEquals(WalletFailure.FAILED, error?.failure)
        assertEquals("Banner must be a PNG image", error?.detail)
    }

    @Test
    fun reportsHttpFailures() = runTest {
        val engine = MockEngine { respond("""{"errors":[{"message":"Unauthorized"}]}""", HttpStatusCode.Unauthorized, jsonHeaders) }
        assertEquals("Unauthorized", PassServiceClient(config, HttpClient(engine)).uploadBanner(ByteArray(4)).leftOrNull()?.detail)
    }

    @Test
    fun requiresServiceUrl() = runTest {
        val error = PassServiceClient(WalletConfig(), HttpClient(MockEngine { error("no request expected") }))
            .uploadBanner(ByteArray(4)).leftOrNull()
        assertEquals(WalletFailure.NOT_CONFIGURED, error?.failure)
    }
}
