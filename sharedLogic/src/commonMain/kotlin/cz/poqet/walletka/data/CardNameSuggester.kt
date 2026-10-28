package cz.poqet.walletka.data

import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine

/**
 * Suggests card names from OCR output and the decoded code payload.
 *
 * The most prominent (tallest) text lines usually carry the brand name on loyalty cards, tickets and
 * membership cards; lines that look like numbers, dates or the code payload itself are skipped.
 */
internal object CardNameSuggester {

    private const val MAX_SUGGESTIONS = 4
    private const val MAX_LENGTH = 32
    private val urlHost = Regex("""^(?:https?://)?(?:www\.)?([a-z0-9-]+)\.[a-z.]{2,}(?:[/:?#].*)?$""", RegexOption.IGNORE_CASE)

    fun suggest(lines: List<TextLine>, codes: List<ScannedCode>): List<String> {
        val payloads = codes.map { it.value.normalizedKey() }.toSet()
        val fromText = lines
            .map { it.copy(text = it.text.trim().replace(Regex("\\s+"), " ")) }
            .filter { it.text.isNameLike() && it.text.normalizedKey() !in payloads }
            .filter { line -> codes.none { code -> code.bounds?.intersects(line.bounds) == true } }
            .sortedByDescending { it.bounds.height }
            .map { it.text.prettified() }
        val fromCodes = codes.mapNotNull { nameFromPayload(it.value) }

        return (fromText + fromCodes)
            .distinctBy { it.lowercase() }
            .take(MAX_SUGGESTIONS)
    }

    internal fun nameFromPayload(value: String): String? {
        val host = urlHost.matchEntire(value.trim())?.groupValues?.get(1) ?: return null
        return host.replace('-', ' ').prettified()
    }

    private fun String.isNameLike(): Boolean {
        if (length !in 2..MAX_LENGTH) return false
        val letters = count { it.isLetter() }
        val digits = count { it.isDigit() }
        return letters >= 2 && letters > digits
    }

    /** "TESCO CLUBCARD" -> "Tesco Clubcard"; mixed case is kept as typed by the brand. */
    private fun String.prettified(): String =
        if (any { it.isLowerCase() } && any { it.isUpperCase() }) this
        else split(' ').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }

    private fun String.normalizedKey(): String = filter { it.isLetterOrDigit() }.lowercase()
}
