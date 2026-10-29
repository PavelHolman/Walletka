package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.ScannedCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PassStringsTest {

    @Test
    fun czechIsPickedForCzechLanguageTagsInAnyForm() {
        listOf("cs", "cs-CZ", "cs_CZ", "CS", " cs-CZ ", "cs-Latn-CZ").forEach {
            assertSame(PassStrings.Czech, PassStrings.forLanguage(it), it)
        }
    }

    @Test
    fun englishIsTheFallback() {
        listOf(null, "", "en", "en-US", "de-DE", "sk", "Base", "c", "csb").forEach {
            assertSame(PassStrings.English, PassStrings.forLanguage(it), it.toString())
        }
    }

    @Test
    fun deviceLanguageResolvesToSomeLabels() {
        val strings = PassStrings.forDevice()
        assertTrue(strings == PassStrings.English || strings == PassStrings.Czech)
    }

    @Test
    fun codeLabelTranslatesOnlyQr() {
        assertEquals("QR kód", PassStrings.Czech.codeLabel(CodeFormat.QR))
        assertEquals("QR code", PassStrings.English.codeLabel(CodeFormat.QR))
        assertEquals("Aztec", PassStrings.Czech.codeLabel(CodeFormat.AZTEC))
    }

    @Test
    fun czechLabelsEndUpInBothPasses() {
        val card = CardDraft(
            walletId = "serial-1",
            name = "Kavárna",
            code = ScannedCode(CodeFormat.EAN_13, "8590000000001"),
            backgroundColor = CardColor(26, 35, 126),
            banner = null,
            details = CardDetails(memberNumber = "123", memberName = "Jana", expiryDate = "2027-05-31", notes = "Hi"),
        )
        val apple = Json.parseToJsonElement(PassJson.apple(card, WalletConfig(), "1", PassStrings.Czech)).jsonObject
        val storeCard = apple["storeCard"]!!.jsonObject
        fun labels(key: String) = storeCard[key]!!.jsonArray.map { it.jsonObject["label"]!!.jsonPrimitive.content }
        assertEquals(listOf("ČLEN", "ČÍSLO"), labels("secondaryFields"))
        assertEquals(listOf("PLATNOST DO"), labels("auxiliaryFields"))
        assertEquals(listOf("Číslo karty", "Člen", "Platnost do", "Poznámky"), labels("backFields"))

        val google = PassJson.googleServiceInput(card, WalletConfig(), strings = PassStrings.Czech)
        assertEquals(
            listOf("Číslo karty", "Člen", "Platnost do", "Poznámky"),
            google["textModules"]!!.jsonArray.map { it.jsonObject["header"]!!.jsonPrimitive.content },
        )
        val config = WalletConfig(googleIssuerId = "3388000000000000000", googleIssuerEmail = "issuer@example.com")
        val jwt = Json.parseToJsonElement(PassJson.google(card, config, "abc", strings = PassStrings.Czech)).jsonObject
        val header = jwt["payload"]!!.jsonObject["genericObjects"]!!.jsonArray[0].jsonObject["header"]!!.jsonObject
        assertEquals("cs", header["defaultValue"]!!.jsonObject["language"]!!.jsonPrimitive.content)
    }
}
