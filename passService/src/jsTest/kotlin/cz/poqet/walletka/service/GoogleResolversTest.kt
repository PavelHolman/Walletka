package cz.poqet.walletka.service

import cz.poqet.walletka.api.WalletkaApi
import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Runs the app's Google Wallet operations through graphql-js against the Worker's resolvers and a fake Google. */
class GoogleResolversTest {

    private val baseUrl = "https://passes.example.com"
    private val calls = mutableListOf<String>()
    private var objectExists = false

    private val fakeGoogle = HttpTransport { request ->
        calls += request.method + " " + request.url.removePrefix(GoogleWalletIssuer.API_BASE)
        when {
            request.url == GoogleServiceAccount.DEFAULT_TOKEN_URI -> HttpResponse(200, """{"access_token":"t","expires_in":3600}""")
            request.method == "POST" && request.url.endsWith("genericObject") -> HttpResponse(200, "{}").also { objectExists = true }
            request.url.contains("genericObject/") -> HttpResponse(if (objectExists) 200 else 404, "{}")
            else -> HttpResponse(200, "{}")
        }
    }

    private val fakeCrypto = object : PassCrypto {
        override suspend fun sha1(data: ByteArray) = ByteArray(20)
        override suspend fun sha256(data: ByteArray) = ByteArray(32)
        override suspend fun signRsaSha256(data: ByteArray) = byteArrayOf(1, 2, 3)
    }

    private val issuer = GoogleWalletIssuer(
        issuerId = "3388000000000000000",
        account = GoogleServiceAccount("walletka@example.iam.gserviceaccount.com", "", GoogleServiceAccount.DEFAULT_TOKEN_URI),
        crypto = fakeCrypto,
        http = fakeGoogle,
        now = { 1_793_000_000 },
    )

    private val passInput: dynamic = jsObject {
        id = "card-1"
        cardTitle = "Walletka"
        header = "Coffee Club"
        hexBackgroundColor = "#1a237e"
        barcodeType = "QR_CODE"
        barcodeValue = "hello"
    }

    private suspend fun execute(issuer: GoogleWalletIssuer?, query: String, variables: dynamic): dynamic {
        val root = jsObject {}
        addGoogleResolvers(root, issuer, baseUrl)
        return graphql(
            jsObject {
                schema = buildSchema(WalletkaApi.SCHEMA)
                source = query
                variableValues = variables
                rootValue = root
            },
        ).await()
    }

    @Test
    fun saveUpdateAndDeleteReachGoogle() = runTest {
        val saved = execute(issuer, WalletkaApi.SAVE_GOOGLE_PASS, jsObject { this.pass = passInput })
        assertEquals(null, saved.errors, JSON.stringify(saved.errors))
        val link = saved.data.saveGooglePass
        assertEquals("3388000000000000000.card-1", link.objectId)
        assertEquals(3, (link.jwt as String).split(".").size)
        assertEquals(GoogleWalletIssuer.SAVE_URL + link.jwt, link.saveUrl)

        val updated = execute(issuer, WalletkaApi.UPDATE_GOOGLE_PASS, jsObject { this.pass = passInput })
        assertEquals(true, updated.data.updateGooglePass.found)

        val deleted = execute(issuer, WalletkaApi.DELETE_GOOGLE_PASS, jsObject { id = "card-1" })
        assertEquals(true, deleted.data.deleteGooglePass)
        assertEquals("PATCH genericObject/3388000000000000000.card-1", calls.last())
    }

    @Test
    fun missingObjectIsNotAnError() = runTest {
        val updated = execute(issuer, WalletkaApi.UPDATE_GOOGLE_PASS, jsObject { this.pass = passInput })
        assertEquals(null, updated.errors)
        assertEquals(false, updated.data.updateGooglePass.found)
    }

    @Test
    fun invalidInputIsRejectedBeforeCallingGoogle() = runTest {
        passInput.barcodeType = "MAXICODE"
        val result = execute(issuer, WalletkaApi.SAVE_GOOGLE_PASS, jsObject { this.pass = passInput })
        assertTrue((result.errors[0].message as String).contains("Unsupported barcode type"))
        assertEquals(emptyList(), calls)
    }

    @Test
    fun unconfiguredServiceSaysSo() = runTest {
        val result = execute(null, WalletkaApi.SAVE_GOOGLE_PASS, jsObject { this.pass = passInput })
        assertEquals("Google Wallet is not configured on the pass service", result.errors[0].message)
    }
}
