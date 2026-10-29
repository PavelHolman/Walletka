package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals

class CardNameSuggesterTest {

    private fun line(text: String, top: Float, height: Float, left: Float = 0.1f, right: Float = 0.9f, confidence: Float = 1f) =
        TextLine(text, NormalizedRect(left, top, right, top + height), confidence)

    private val ean = ScannedCode(CodeFormat.EAN_13, "8591234567890", NormalizedRect(0.2f, 0.65f, 0.8f, 0.85f))

    private fun best(vararg lines: TextLine, codes: List<ScannedCode> = listOf(ean)) =
        CardNameSuggester.suggest(lines.toList(), codes).firstOrNull()

    @Test
    fun tallestNameLikeLineComesFirst() {
        val lines = listOf(
            line("Member since 2021", top = 0.6f, height = 0.03f),
            line("COFFEE CLUB", top = 0.1f, height = 0.12f),
            line("1234 5678 9012", top = 0.7f, height = 0.05f),
        )
        val names = CardNameSuggester.suggest(lines, listOf(ScannedCode(CodeFormat.EAN_13, "123456789012")))
        assertEquals(listOf("Coffee Club"), names)
    }

    @Test
    fun skipsTextPrintedInsideTheCode() {
        val code = ScannedCode(CodeFormat.CODE_128, "ABC", NormalizedRect(0f, 0.5f, 1f, 0.8f))
        val names = CardNameSuggester.suggest(listOf(line("Human readable", top = 0.6f, height = 0.1f)), listOf(code))
        assertEquals(emptyList(), names)
    }

    @Test
    fun keepsBrandStyling() {
        val names = CardNameSuggester.suggest(listOf(line("dm drogerie markt", 0f, 0.1f), line("IKEA Family", 0.2f, 0.05f)), emptyList())
        assertEquals(listOf("dm drogerie markt", "IKEA Family"), names)
    }

    @Test
    fun derivesNameFromUrlPayload() {
        val names = CardNameSuggester.suggest(emptyList(), listOf(ScannedCode(CodeFormat.QR, "https://www.my-gym.com/members/42")))
        assertEquals(listOf("My Gym"), names)
    }

    @Test
    fun tescoClubcardOnOneRow() {
        val name = best(
            line("TESCO", top = 0.12f, height = 0.1f, left = 0.1f, right = 0.45f),
            line("Clubcard", top = 0.14f, height = 0.07f, left = 0.48f, right = 0.85f),
            line("Číslo karty: 6340 1234", top = 0.55f, height = 0.03f),
            line("www.tesco.cz", top = 0.9f, height = 0.025f),
        )
        assertEquals("Tesco Clubcard", name)
    }

    @Test
    fun dmBrandWinsOverTagline() {
        val name = best(
            line("dm", top = 0.08f, height = 0.16f, left = 0.35f, right = 0.6f),
            line("drogerie markt", top = 0.26f, height = 0.05f, left = 0.3f, right = 0.7f),
            line("Věrnostní karta", top = 0.4f, height = 0.06f),
        )
        assertEquals("dm", name)
    }

    @Test
    fun rossmannIgnoresBoilerplateAndContacts() {
        val name = best(
            line("Zákaznická karta", top = 0.05f, height = 0.07f),
            line("ROSSMANN", top = 0.2f, height = 0.11f),
            line("+420 800 123 456", top = 0.88f, height = 0.04f),
            line("info@rossmann.cz", top = 0.93f, height = 0.04f),
        )
        assertEquals("Rossmann", name)
    }

    @Test
    fun lidlPlusShouted() {
        val name = best(
            line("LIDL PLUS", top = 0.1f, height = 0.09f),
            line("Platí do 12/2027", top = 0.5f, height = 0.04f),
        )
        assertEquals("Lidl Plus", name)
    }

    @Test
    fun ikeaFamilyStackedLinesAreJoined() {
        val name = best(
            line("IKEA", top = 0.1f, height = 0.1f, left = 0.3f, right = 0.7f),
            line("FAMILY", top = 0.22f, height = 0.09f, left = 0.25f, right = 0.75f),
            line("Loyalty card", top = 0.45f, height = 0.04f),
        )
        assertEquals("Ikea Family", name)
    }

    @Test
    fun decathlonStripsCardWordAndSkipsTheCodeDigits() {
        val name = best(
            line("8591234567890", top = 0.86f, height = 0.05f),
            line("DECATHLON card", top = 0.1f, height = 0.1f),
            line("Member", top = 0.3f, height = 0.06f),
        )
        assertEquals("Decathlon", name)
    }

    @Test
    fun lowConfidenceLinesAreIgnored() {
        val name = best(
            line("Xq%rl", top = 0.05f, height = 0.2f, confidence = 0.1f),
            line("Coffee Club", top = 0.3f, height = 0.08f),
        )
        assertEquals("Coffee Club", name)
    }

    @Test
    fun prefersTopOfCardOverSimilarTextLower() {
        val name = best(
            line("Thank you", top = 0.55f, height = 0.06f),
            line("Bean & Leaf", top = 0.08f, height = 0.06f),
        )
        assertEquals("Bean & Leaf", name)
    }
}
