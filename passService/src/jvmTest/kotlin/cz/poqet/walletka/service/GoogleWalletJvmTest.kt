package cz.poqet.walletka.service

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.net.URLDecoder
import java.security.Signature
import java.security.cert.CertificateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Records requests and answers them like Google's OAuth and Wallet Objects endpoints. */
private class FakeGoogle(var objectExists: Boolean = false, var classExists: Boolean = false) : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    var tokensIssued = 0
    var rejectNextToken = false

    override suspend fun send(request: HttpRequest): HttpResponse {
        requests += request
        if (request.url == GoogleServiceAccount.DEFAULT_TOKEN_URI) {
            tokensIssued++
            return HttpResponse(200, """{"access_token":"token-$tokensIssued","expires_in":3600,"token_type":"Bearer"}""")
        }
        if (rejectNextToken) {
            rejectNextToken = false
            return HttpResponse(401, """{"error":{"code":401,"message":"Invalid Credentials"}}""")
        }
        val path = request.url.removePrefix(GoogleWalletIssuer.API_BASE)
        return when {
            path.startsWith("genericClass/") -> if (classExists) HttpResponse(200, "{}") else HttpResponse(404, "{}")
            path == "genericClass" -> HttpResponse(200, "{}").also { classExists = true }
            path == "genericObject" -> if (objectExists) HttpResponse(409, "{}") else HttpResponse(200, request.body!!).also { objectExists = true }
            path.startsWith("genericObject/") -> if (objectExists) HttpResponse(200, "{}") else HttpResponse(404, """{"error":{"message":"not found"}}""")
            else -> HttpResponse(500, "{}")
        }
    }

    fun walletCalls() = requests.filter { it.url.startsWith(GoogleWalletIssuer.API_BASE) }
        .map { it.method + " " + it.url.removePrefix(GoogleWalletIssuer.API_BASE) }
}

class GoogleWalletJvmTest {

    private fun resource(name: String) = checkNotNull(javaClass.getResource("/test-certs/$name")).readText()

    private val account = GoogleServiceAccount.parse(
        buildJsonObject {
            put("type", "service_account")
            put("client_email", "walletka@example.iam.gserviceaccount.com")
            put("private_key", resource("pass-key.pem"))
        }.toString(),
    )!!
    private val crypto = JvmPassCrypto(Pem.privateKeyPkcs8(account.privateKeyPem))
    private var clock = 1_793_000_000L
    private val google = FakeGoogle()
    private val issuer = GoogleWalletIssuer("3388000000000000000", account, crypto, google, now = { clock })

    private val baseUrl = "https://passes.example.com"
    private val pass = GooglePass(
        id = "0b7c2a9e-1b0c-4a43-9e7d-1b2c3d4e5f60",
        cardTitle = "Walletka",
        header = "  Coffee Club ",
        hexBackgroundColor = "#1A237E",
        barcodeType = "EAN_13",
        barcodeValue = "8590000000001",
        heroImageUrl = "$baseUrl/banners/0123456789abcdef0123456789abcdef.png",
    )

    private fun claims(jwt: String): JsonObject {
        val (header, payload, signature) = jwt.split(".")
        val publicKey = CertificateFactory.getInstance("X.509")
            .generateCertificate(Pem.certificates(resource("pass-cert.pem")).first().inputStream()).publicKey
        val valid = Signature.getInstance("SHA256withRSA").run {
            initVerify(publicKey)
            update("$header.$payload".encodeToByteArray())
            verify(Jwt.decode(signature))
        }
        assertTrue(valid, "RS256 signature must verify with the account's public key")
        assertEquals("""{"alg":"RS256","typ":"JWT"}""", Jwt.decode(header).decodeToString())
        return Json.parseToJsonElement(Jwt.decode(payload).decodeToString()).jsonObject
    }

    @Test
    fun parsesServiceAccountKeyFiles() {
        assertEquals("walletka@example.iam.gserviceaccount.com", account.clientEmail)
        assertEquals(GoogleServiceAccount.DEFAULT_TOKEN_URI, account.tokenUri)
        assertNull(GoogleServiceAccount.parse("""{"client_email":"x@example.com"}"""))
        assertNull(GoogleServiceAccount.parse("not json"))
    }

    @Test
    fun saveCreatesClassAndObjectAndSignsASaveLink() = runTest {
        val link = issuer.save(pass, baseUrl)

        assertEquals(listOf("GET genericClass/3388000000000000000.walletka_generic", "POST genericClass", "POST genericObject"), google.walletCalls())
        val created = Json.parseToJsonElement(google.requests.last().body!!).jsonObject
        assertEquals("3388000000000000000.${pass.id}", created["id"]!!.jsonPrimitive.content)
        assertEquals("#1a237e", created["hexBackgroundColor"]!!.jsonPrimitive.content)
        assertEquals("Coffee Club", created["header"]!!.jsonObject["defaultValue"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals(pass.heroImageUrl, created["heroImage"]!!.jsonObject["sourceUri"]!!.jsonObject["uri"]!!.jsonPrimitive.content)
        assertEquals("Bearer token-1", google.requests.last().headers["authorization"])

        val claims = claims(link.jwt)
        assertEquals(GoogleWalletIssuer.SAVE_URL + link.jwt, link.saveUrl)
        assertEquals("google", claims["aud"]!!.jsonPrimitive.content)
        assertEquals("savetowallet", claims["typ"]!!.jsonPrimitive.content)
        assertEquals(account.clientEmail, claims["iss"]!!.jsonPrimitive.content)
        val saved = claims["payload"]!!.jsonObject["genericObjects"]!!.jsonArray.single().jsonObject
        assertEquals(link.objectId, saved["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun saveForwardsLogoAndDetails() = runTest {
        val detailed = GooglePass(
            id = pass.id,
            cardTitle = pass.cardTitle,
            header = pass.header,
            hexBackgroundColor = pass.hexBackgroundColor,
            barcodeType = pass.barcodeType,
            barcodeValue = pass.barcodeValue,
            heroImageUrl = null,
            logoUrl = "$baseUrl/banners/fedcba9876543210fedcba9876543210.png",
            subheader = " Jana Nováková ",
            expiryDate = "2027-05-31",
            textModules = listOf(GoogleTextModule("member_number", "Card number", " 1234 "), GoogleTextModule("notes", "Notes", "Valid in all cafés")),
        )
        issuer.save(detailed, baseUrl)

        val created = Json.parseToJsonElement(google.requests.last().body!!).jsonObject
        assertNull(created["heroImage"])
        assertEquals(detailed.logoUrl, created["logo"]!!.jsonObject["sourceUri"]!!.jsonObject["uri"]!!.jsonPrimitive.content)
        assertEquals("Jana Nováková", created["subheader"]!!.jsonObject["defaultValue"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("2027-05-31T23:59:59Z", created["validTimeInterval"]!!.jsonObject["end"]!!.jsonObject["date"]!!.jsonPrimitive.content)
        val modules = created["textModulesData"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("member_number", "notes"), modules.map { it["id"]!!.jsonPrimitive.content })
        assertEquals("1234", modules.first()["body"]!!.jsonPrimitive.content)
        assertEquals("Card number", modules.first()["header"]!!.jsonPrimitive.content)
    }

    @Test
    fun plainPassHasNoDetailFields() = runTest {
        issuer.save(pass, baseUrl)
        val created = Json.parseToJsonElement(google.requests.last().body!!).jsonObject
        for (field in listOf("logo", "subheader", "validTimeInterval", "textModulesData")) assertNull(created[field], field)
    }

    @Test
    fun tokenRequestIsASignedJwtBearerGrant() = runTest {
        issuer.save(pass, baseUrl)
        val tokenRequest = google.requests.first()
        assertEquals("POST", tokenRequest.method)
        val form = tokenRequest.body!!.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        assertEquals(GoogleAccessTokens.JWT_BEARER_GRANT, form["grant_type"])
        val claims = claims(form.getValue("assertion"))
        assertEquals(GoogleAccessTokens.WALLET_SCOPE, claims["scope"]!!.jsonPrimitive.content)
        assertEquals(GoogleServiceAccount.DEFAULT_TOKEN_URI, claims["aud"]!!.jsonPrimitive.content)
        assertEquals(clock + 3600, claims["exp"]!!.jsonPrimitive.long)
    }

    @Test
    fun tokensAreCachedUntilShortlyBeforeExpiry() = runTest {
        issuer.save(pass, baseUrl)
        issuer.update(pass, baseUrl)
        assertEquals(1, google.tokensIssued)
        clock += 3600 - 30
        issuer.update(pass, baseUrl)
        assertEquals(2, google.tokensIssued)
    }

    @Test
    fun rejectedTokenIsRefreshedOnce() = runTest {
        google.objectExists = true
        issuer.update(pass, baseUrl)
        google.rejectNextToken = true
        assertTrue(issuer.update(pass, baseUrl))
        assertEquals(2, google.tokensIssued)
    }

    @Test
    fun savingAnExistingObjectReplacesIt() = runTest {
        google.classExists = true
        google.objectExists = true
        issuer.save(pass, baseUrl)
        assertEquals(listOf("GET genericClass/3388000000000000000.walletka_generic", "POST genericObject", "PUT genericObject/3388000000000000000.${pass.id}"), google.walletCalls())
    }

    @Test
    fun updateReportsMissingObjects() = runTest {
        assertFalse(issuer.update(pass, baseUrl))
        google.objectExists = true
        assertTrue(issuer.update(pass, baseUrl))
        assertEquals("PUT", google.requests.last().method)
    }

    @Test
    fun deleteExpiresTheObject() = runTest {
        assertFalse(issuer.expire(pass.id))
        google.objectExists = true
        assertTrue(issuer.expire(pass.id))
        val patch = google.requests.last()
        assertEquals("PATCH", patch.method)
        assertEquals("""{"state":"EXPIRED"}""", patch.body)
    }

    @Test
    fun validatesInput() = runTest {
        fun rejects(invalid: GooglePass, fragment: String) {
            val error = runCatching { GoogleWalletIssuer.validate(invalid, baseUrl) }.exceptionOrNull()
            assertTrue(error is ServiceException && fragment in error.message.orEmpty(), "$error")
        }
        fun copy(
            id: String = pass.id,
            header: String = pass.header,
            color: String = pass.hexBackgroundColor,
            type: String = pass.barcodeType,
            value: String = pass.barcodeValue,
            hero: String? = pass.heroImageUrl,
            logo: String? = null,
            subheader: String? = null,
            expiry: String? = null,
            modules: List<GoogleTextModule> = emptyList(),
        ) = GooglePass(id, pass.cardTitle, header, color, type, value, hero, logo, subheader, expiry, modules)

        rejects(copy(id = "../classes/x"), "Pass id")
        rejects(copy(header = " "), "header")
        rejects(copy(header = "x".repeat(101)), "header")
        rejects(copy(color = "red"), "hexBackgroundColor")
        rejects(copy(type = "MAXICODE"), "barcode type")
        rejects(copy(value = ""), "barcodeValue")
        rejects(copy(hero = "https://evil.example.com/banners/0123456789abcdef0123456789abcdef.png"), "heroImageUrl")
        rejects(copy(hero = "$baseUrl/banners/../secret.png"), "heroImageUrl")
        assertNull(GoogleWalletIssuer.validate(copy(hero = null), baseUrl).heroImageUrl)
        rejects(copy(logo = "https://evil.example.com/logo.png"), "logoUrl")
        rejects(copy(subheader = "x".repeat(101)), "subheader")
        rejects(copy(expiry = "31.5.2027"), "expiryDate")
        rejects(copy(expiry = "2027-13-01"), "expiryDate")
        rejects(copy(modules = listOf(GoogleTextModule("bad id", "Notes", "x"))), "textModules id")
        rejects(copy(modules = listOf(GoogleTextModule("notes", "Notes", " "))), "textModules body")
        rejects(copy(modules = listOf(GoogleTextModule("notes", "A", "x"), GoogleTextModule("notes", "B", "y"))), "unique")
        rejects(copy(modules = List(11) { GoogleTextModule("m$it", "H", "x") }), "At most")
        val normalised = GoogleWalletIssuer.validate(copy(subheader = "  ", expiry = " 2027-05-31 "), baseUrl)
        assertNull(normalised.subheader)
        assertEquals("2027-05-31", normalised.expiryDate)
        assertTrue(google.requests.isEmpty())
    }
}
