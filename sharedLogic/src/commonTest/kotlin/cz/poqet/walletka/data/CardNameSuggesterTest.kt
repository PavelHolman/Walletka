package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals

class CardNameSuggesterTest {

    private fun line(text: String, top: Float, height: Float) = TextLine(text, NormalizedRect(0.1f, top, 0.9f, top + height))

    @Test
    fun tallestNameLikeLineComesFirst() {
        val lines = listOf(
            line("Member since 2021", top = 0.6f, height = 0.03f),
            line("COFFEE CLUB", top = 0.1f, height = 0.12f),
            line("1234 5678 9012", top = 0.7f, height = 0.05f),
        )
        val names = CardNameSuggester.suggest(lines, listOf(ScannedCode(CodeFormat.EAN_13, "123456789012")))
        assertEquals(listOf("Coffee Club", "Member since 2021"), names)
    }

    @Test
    fun skipsTextPrintedInsideTheCode() {
        val code = ScannedCode(CodeFormat.CODE_128, "ABC", NormalizedRect(0f, 0.5f, 1f, 0.8f))
        val names = CardNameSuggester.suggest(listOf(line("Human readable", top = 0.6f, height = 0.1f)), listOf(code))
        assertEquals(emptyList(), names)
    }

    @Test
    fun keepsMixedCaseBrandNames() {
        val names = CardNameSuggester.suggest(listOf(line("dm drogerie markt", 0f, 0.1f), line("IKEA Family", 0.2f, 0.05f)), emptyList())
        assertEquals(listOf("Dm Drogerie Markt", "IKEA Family"), names)
    }

    @Test
    fun derivesNameFromUrlPayload() {
        val names = CardNameSuggester.suggest(emptyList(), listOf(ScannedCode(CodeFormat.QR, "https://www.my-gym.com/members/42")))
        assertEquals(listOf("My Gym"), names)
    }
}
