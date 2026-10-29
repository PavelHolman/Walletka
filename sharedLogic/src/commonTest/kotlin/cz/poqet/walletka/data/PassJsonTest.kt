package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PassJsonTest {

    private val card = CardDraft(
        walletId = "serial-1",
        name = "Coffee Club",
        code = ScannedCode(CodeFormat.EAN_13, "8590000000001"),
        backgroundColor = CardColor(26, 35, 126),
        banner = null,
    )

    @Test
    fun applePassUsesCode128ForEan() {
        val config = WalletConfig()
        val pass = Json.parseToJsonElement(PassJson.apple(card, config, serialNumber = "1")).jsonObject
        val barcode = pass["barcodes"]!!.jsonArray.first().jsonObject
        assertEquals("PKBarcodeFormatCode128", barcode["format"]!!.jsonPrimitive.content)
        assertEquals("8590000000001", barcode["message"]!!.jsonPrimitive.content)
        assertEquals("rgb(255, 255, 255)", pass["foregroundColor"]!!.jsonPrimitive.content)
    }

    @Test
    fun googlePassKeepsEan() {
        val config = WalletConfig(googleIssuerId = "3388000000000000000", googleIssuerEmail = "issuer@example.com")
        val root = Json.parseToJsonElement(PassJson.google(card, config, objectId = "abc")).jsonObject
        val obj = root["payload"]!!.jsonObject["genericObjects"]!!.jsonArray.first().jsonObject
        assertEquals("3388000000000000000.abc", obj["id"]!!.jsonPrimitive.content)
        assertEquals("EAN_13", obj["barcode"]!!.jsonObject["type"]!!.jsonPrimitive.content)
        assertEquals("#1a237e", obj["hexBackgroundColor"]!!.jsonPrimitive.content)
    }

    private val detailed = card.copy(
        logo = StoredImage("/tmp/logo.png"),
        details = CardDetails(memberNumber = " 1234 5678 ", memberName = "Jana Nováková", expiryDate = "2027-05-31", notes = "Valid in all cafés"),
    )

    private fun JsonObject.fields(name: String) =
        this["storeCard"]!!.jsonObject[name]!!.jsonArray.associate { field ->
            field.jsonObject["key"]!!.jsonPrimitive.content to field.jsonObject["value"]!!.jsonPrimitive.content
        }

    @Test
    fun applePassWithLogoDropsLogoText() {
        val plain = Json.parseToJsonElement(PassJson.apple(card, WalletConfig(), serialNumber = "1")).jsonObject
        assertEquals("Coffee Club", plain["logoText"]!!.jsonPrimitive.content)
        val withLogo = Json.parseToJsonElement(PassJson.apple(detailed, WalletConfig(), serialNumber = "1")).jsonObject
        assertFalse("logoText" in withLogo)
        assertEquals("Coffee Club", withLogo["description"]!!.jsonPrimitive.content)
    }

    @Test
    fun applePassShowsDetailsOnFrontAndBack() {
        val pass = Json.parseToJsonElement(PassJson.apple(detailed, WalletConfig(), serialNumber = "1")).jsonObject
        assertEquals(mapOf("memberName" to "Jana Nováková", "memberNumber" to "1234 5678"), pass.fields("secondaryFields"))
        assertEquals(mapOf("expiry" to "2027-05-31T00:00:00Z"), pass.fields("auxiliaryFields"))
        assertEquals(
            listOf("memberNumberBack", "memberNameBack", "expiryBack", "notes"),
            pass.fields("backFields").keys.toList(),
        )
        assertEquals("Valid in all cafés", pass.fields("backFields")["notes"])
        assertEquals("2027-05-31T23:59:59Z", pass["expirationDate"]!!.jsonPrimitive.content)
    }

    @Test
    fun applePassWithoutDetailsHasNoExtraFields() {
        val pass = Json.parseToJsonElement(PassJson.apple(card, WalletConfig(), serialNumber = "1")).jsonObject
        assertTrue(pass.fields("backFields").isEmpty())
        assertTrue(pass.fields("auxiliaryFields").isEmpty())
        assertNull(pass["expirationDate"])
    }

    @Test
    fun applePassIgnoresAnInvalidExpiry() {
        val pass = Json.parseToJsonElement(
            PassJson.apple(card.copy(details = CardDetails.Empty.copy(expiryDate = "2027-02-30")), WalletConfig(), serialNumber = "1"),
        ).jsonObject
        assertNull(pass["expirationDate"])
    }

    @Test
    fun googlePassCarriesLogoAndTextModules() {
        val config = WalletConfig(googleIssuerId = "3388000000000000000", googleIssuerEmail = "issuer@example.com")
        val root = Json.parseToJsonElement(
            PassJson.google(detailed, config, objectId = "abc", logoUrl = "https://passes.example.com/banners/logo.png"),
        ).jsonObject
        val obj = root["payload"]!!.jsonObject["genericObjects"]!!.jsonArray.first().jsonObject
        assertEquals(
            "https://passes.example.com/banners/logo.png",
            obj["logo"]!!.jsonObject["sourceUri"]!!.jsonObject["uri"]!!.jsonPrimitive.content,
        )
        val modules = obj["textModulesData"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("member_number", "member_name", "expiry", "notes"), modules.map { it["id"]!!.jsonPrimitive.content })
        assertEquals("1234 5678", modules.first()["body"]!!.jsonPrimitive.content)
        assertEquals("Jana Nováková", obj["subheader"]!!.jsonObject["defaultValue"]!!.jsonObject["value"]!!.jsonPrimitive.content)
        assertEquals("2027-05-31T23:59:59Z", obj["validTimeInterval"]!!.jsonObject["end"]!!.jsonObject["date"]!!.jsonPrimitive.content)
    }

    @Test
    fun googlePassWithoutDetailsHasNoTextModules() {
        val config = WalletConfig(googleIssuerId = "3388000000000000000", googleIssuerEmail = "issuer@example.com")
        val root = Json.parseToJsonElement(PassJson.google(card, config, objectId = "abc")).jsonObject
        val obj = root["payload"]!!.jsonObject["genericObjects"]!!.jsonArray.first().jsonObject
        assertFalse("textModulesData" in obj || "logo" in obj || "validTimeInterval" in obj)
    }
}
