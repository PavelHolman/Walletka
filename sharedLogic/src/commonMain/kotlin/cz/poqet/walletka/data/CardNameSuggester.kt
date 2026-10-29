package cz.poqet.walletka.data

import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine
import kotlin.math.abs

/**
 * Suggests card names from OCR output and the decoded code payload, best guess first.
 *
 * The brand on loyalty cards, tickets and membership cards is usually the most prominent text: the tallest
 * lines, near the top and centre, away from the code. Lines that look like numbers, dates, contacts, the code
 * payload or card boilerplate ("Member card", "Věrnostní karta") are dropped, and a brand split over two lines
 * of the same size ("IKEA" / "FAMILY") is joined back together.
 */
internal object CardNameSuggester {

    private const val MAX_SUGGESTIONS = 4
    private const val MAX_LENGTH = 32
    private const val MAX_WORDS = 4
    private const val MIN_CONFIDENCE = 0.3f
    private val urlHost = Regex("""^(?:https?://)?(?:www\.)?([a-z0-9-]+)\.[a-z.]{2,}(?:[/:?#].*)?$""", RegexOption.IGNORE_CASE)
    private val email = Regex("""\S+@\S+\.\S+""")
    private val webAddress = Regex("""(?:https?://|www\.)|\b[a-z0-9-]+\.(?:cz|sk|com|net|org|eu|de|at|pl|hu|info)\b""", RegexOption.IGNORE_CASE)
    private val longDigitRun = Regex("""\d{4,}""")
    private val decorations = Regex("""[®™©*•|]""")

    /** Words that describe the card rather than name it; a line made only of these is not a name. */
    private val boilerplate = setOf(
        "member", "members", "membership", "card", "cards", "loyalty", "customer", "client", "rewards", "program",
        "programme", "account", "gift",
        "karta", "karty", "kartu", "vernostni", "zakaznicka", "zakaznicky", "clenska", "clensky", "clen",
        "klientska", "klient", "darkova",
    )

    /** Filler that, together with [boilerplate], still does not make a name ("Your loyalty card"). */
    private val fillers = setOf("the", "your", "my", "of", "for", "a", "vase", "vas", "moje", "muj", "pro")

    /** Words that only appear in labels and fine print ("Member since", "Platnost do", "Card number"). */
    private val labelWords = setOf(
        "since", "valid", "until", "expires", "expiry", "number", "no", "holder", "name", "scan", "present", "terms",
        "conditions", "issued", "date", "platnost", "plati", "do", "od", "cislo", "drzitel", "jmeno", "predlozte",
        "naskenujte", "podminky", "vydano", "datum", "tel", "phone", "info", "www",
    )

    fun suggest(lines: List<TextLine>, codes: List<ScannedCode>): List<String> {
        val payloads = codes.map { it.value.normalizedKey() }.filter { it.isNotEmpty() }.toSet()
        val codeBounds = codes.mapNotNull { it.bounds }
        val candidates = lines
            .filter { it.confidence >= MIN_CONFIDENCE }
            .map { it.copy(text = it.text.cleaned()) }
            .filter { line -> line.text.isNameLike(payloads) && codeBounds.none { it.intersects(line.bounds) } }
            .map { Candidate(it.text.withoutBoilerplateEdges(), it.bounds, prominence(it)) }
        val fromText = (candidates + joined(candidates))
            .filter { it.text.isNotEmpty() }
            .sortedByDescending { it.score }
            .map { it.text.prettified() }
        val fromCodes = codes.mapNotNull { nameFromPayload(it.value) }

        return (fromText + fromCodes)
            .distinctBy { it.lowercase() }
            .take(MAX_SUGGESTIONS)
    }

    internal fun nameFromPayload(value: String): String? {
        val host = urlHost.matchEntire(value.trim())?.groupValues?.get(1) ?: return null
        return host.split('-').joinToString(" ") { word -> word.lowercase().replaceFirstChar { it.uppercaseChar() } }
    }

    private class Candidate(val text: String, val bounds: NormalizedRect, val score: Float)

    /** Text height dominates; position near the top and horizontal centre and recognizer confidence refine it. */
    private fun prominence(line: TextLine): Float {
        val bounds = line.bounds
        val centerX = (bounds.left + bounds.right) / 2
        val centerY = (bounds.top + bounds.bottom) / 2
        val vertical = 1.15f - 0.3f * centerY.coerceIn(0f, 1f)
        val horizontal = 1f - 0.2f * abs(centerX - 0.5f).coerceAtMost(0.5f)
        val confidence = 0.6f + 0.4f * line.confidence.coerceIn(0f, 1f)
        val shortToken = if (line.text.count { it.isLetter() } <= 2) 0.7f else 1f
        return bounds.height * vertical * horizontal * confidence * shortToken
    }

    /**
     * Pairs of lines that read as one brand: the same size and either side by side on one row or stacked
     * right under each other with overlapping columns.
     */
    private fun joined(candidates: List<Candidate>): List<Candidate> =
        candidates.flatMap { a -> candidates.mapNotNull { b -> if (a !== b) join(a, b) else null } }

    private fun join(a: Candidate, b: Candidate): Candidate? {
        val ha = a.bounds.height
        val hb = b.bounds.height
        if (ha <= 0f || hb <= 0f) return null
        val sizeRatio = minOf(ha, hb) / maxOf(ha, hb)
        val verticalOverlap = minOf(a.bounds.bottom, b.bounds.bottom) - maxOf(a.bounds.top, b.bounds.top)
        val sameRow = verticalOverlap >= 0.5f * minOf(ha, hb) && sizeRatio >= 0.5f &&
            b.bounds.left >= a.bounds.left && b.bounds.left - a.bounds.right in -0.2f * minOf(ha, hb)..1.2f * maxOf(ha, hb)
        val horizontalOverlap = minOf(a.bounds.right, b.bounds.right) - maxOf(a.bounds.left, b.bounds.left)
        val stacked = sizeRatio >= 0.75f && b.bounds.top >= a.bounds.top && horizontalOverlap > 0f &&
            b.bounds.top - a.bounds.bottom in -0.3f * minOf(ha, hb)..0.6f * (ha + hb) / 2
        if (!sameRow && !stacked) return null
        val text = "${a.text} ${b.text}"
        if (text.length > MAX_LENGTH || text.split(' ').size > MAX_WORDS) return null
        val bounds = NormalizedRect(
            minOf(a.bounds.left, b.bounds.left), minOf(a.bounds.top, b.bounds.top),
            maxOf(a.bounds.right, b.bounds.right), maxOf(a.bounds.bottom, b.bounds.bottom),
        )
        // A complete brand beats either of its halves.
        return Candidate(text, bounds, maxOf(a.score, b.score) * 1.1f)
    }

    private fun String.cleaned(): String =
        replace(decorations, " ").trim().replace(Regex("\\s+"), " ").trim { !it.isLetterOrDigit() && it != '+' && it != '&' }

    private fun String.isNameLike(payloads: Set<String>): Boolean {
        if (length !in 2..MAX_LENGTH || split(' ').size > MAX_WORDS) return false
        val letters = count { it.isLetter() }
        val digits = count { it.isDigit() }
        if (letters < 2 || digits >= letters || longDigitRun.containsMatchIn(this)) return false
        if (email.containsMatchIn(this) || webAddress.containsMatchIn(this) || endsWith(':')) return false
        val key = normalizedKey()
        if (key in payloads || payloads.any { it.length >= 4 && (it in key || key in it) }) return false
        val words = words()
        return words.none { it in labelWords } && !words.all { it in boilerplate || it in fillers }
    }

    /** "IKEA Family card" -> "IKEA Family"; boilerplate inside the name is kept. */
    private fun String.withoutBoilerplateEdges(): String {
        val parts = split(' ').toMutableList()
        while (parts.size > 1 && parts.first().wordKey() in boilerplate) parts.removeAt(0)
        while (parts.size > 1 && parts.last().wordKey() in boilerplate) parts.removeAt(parts.lastIndex)
        return parts.joinToString(" ")
    }

    /**
     * Shouted lines are title-cased ("TESCO CLUBCARD" -> "Tesco Clubcard", short acronyms like "OBI" stay);
     * lines with any lowercase keep the brand's own styling ("dm drogerie markt", "IKEA Family"), except long
     * shouted words next to normal ones ("TESCO Clubcard" -> "Tesco Clubcard").
     */
    private fun String.prettified(): String {
        val words = split(' ')
        val shouted = none { it.isLowerCase() }
        return words.joinToString(" ") { word ->
            val letters = word.count { it.isLetter() }
            val isUpper = word.none { it.isLowerCase() } && word.any { it.isUpperCase() }
            when {
                !isUpper -> word
                shouted && letters <= 3 -> word
                !shouted && letters <= 4 -> word
                else -> word.lowercase().replaceFirstChar { it.uppercaseChar() }
            }
        }
    }

    private fun String.words(): List<String> = split(' ').map { it.wordKey() }.filter { it.isNotEmpty() }

    private fun String.wordKey(): String = normalizedKey()

    /** Lowercase letters and digits only, Czech diacritics folded to ASCII. */
    private fun String.normalizedKey(): String = lowercase()
        .map { diacritics[it] ?: it }
        .filter { it.isLetterOrDigit() }
        .joinToString("")

    private val diacritics: Map<Char, Char> = "áčďéěíňóřšťúůýžäöüľĺŕ".zip("acdeeinorstuuyzaoullr").toMap()
}
