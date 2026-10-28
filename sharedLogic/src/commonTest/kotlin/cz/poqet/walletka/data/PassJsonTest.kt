package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.ScannedCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals

class PassJsonTest {

    private val card = CardDraft(
        name = "Coffee Club",
        code = ScannedCode(CodeFormat.EAN_13, "8590000000001"),
        backgroundColor = CardColor(26, 35, 126),
        banner = null,
    )

    @Test
    fun applePassUsesCode128ForEan() {
        val config = WalletConfig(applePassTypeIdentifier = "pass.cz.poqet.walletka", appleTeamIdentifier = "TEAM")
        val pass = Json.parseToJsonElement(PassJson.apple(card, config, serialNumber = "1")).jsonObject
        val barcode = pass["barcodes"]!!.jsonArray.first().jsonObject
        assertEquals("PKBarcodeFormatCode128", barcode["format"]!!.jsonPrimitive.content)
        assertEquals("8590000000001", barcode["message"]!!.jsonPrimitive.content)
        assertEquals("rgb(255, 255, 255)", pass["foregroundColor"]!!.jsonPrimitive.content)
        assertEquals("pass.cz.poqet.walletka", pass["passTypeIdentifier"]!!.jsonPrimitive.content)
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
}
