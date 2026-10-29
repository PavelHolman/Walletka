package cz.poqet.walletka.domain

/** Why a typed code can't be encoded in its symbology; see [CodeFormat.issueWith]. */
enum class CodeIssue {
    /** EAN, UPC and ITF codes hold digits only. */
    DIGITS_ONLY,

    /** The number of digits doesn't fit the symbology ([CodeFormat.expectedLengths]). */
    LENGTH,

    /** The last digit isn't the check digit of the others: most likely a typo. */
    CHECK_DIGIT,

    /** Characters the symbology can't encode (e.g. lowercase letters in Code 39). */
    CHARACTERS,
}

/** Lengths a code of this symbology may have, with or without its check digit; empty when any length goes. */
val CodeFormat.expectedLengths: List<Int>
    get() = when (this) {
        CodeFormat.EAN_13 -> listOf(12, 13)
        CodeFormat.EAN_8 -> listOf(7, 8)
        CodeFormat.UPC_A -> listOf(11, 12)
        CodeFormat.UPC_E -> listOf(6, 7, 8)
        else -> emptyList()
    }

/** What's wrong with [value] as a code of this symbology, or `null` when it can be encoded (blank counts as fine). */
fun CodeFormat.issueWith(value: String): CodeIssue? {
    val code = value.trim()
    if (code.isEmpty()) return null
    return when (this) {
        CodeFormat.QR, CodeFormat.AZTEC, CodeFormat.PDF417, CodeFormat.DATA_MATRIX -> null
        CodeFormat.CODE_128 -> if (code.all { it.code in 32..126 }) null else CodeIssue.CHARACTERS
        CodeFormat.CODE_39, CodeFormat.CODE_93 ->
            if (code.all { it in CODE_39_CHARACTERS }) null else CodeIssue.CHARACTERS
        CodeFormat.CODABAR -> if (code.all { it in CODABAR_CHARACTERS }) null else CodeIssue.CHARACTERS
        CodeFormat.ITF -> when {
            !code.all(Char::isDigit) -> CodeIssue.DIGITS_ONLY
            code.length % 2 != 0 -> CodeIssue.LENGTH
            else -> null
        }
        CodeFormat.EAN_13, CodeFormat.EAN_8, CodeFormat.UPC_A, CodeFormat.UPC_E -> when {
            !code.all(Char::isDigit) -> CodeIssue.DIGITS_ONLY
            code.length !in expectedLengths -> CodeIssue.LENGTH
            // The full length includes the check digit (UPC-E's is computed over its expansion, not checked here).
            this != CodeFormat.UPC_E && code.length == expectedLengths.last() && !hasValidCheckDigit(code) -> CodeIssue.CHECK_DIGIT
            else -> null
        }
    }
}

/** GS1 mod-10: weights 3 and 1 from the right, excluding the check digit. */
private fun hasValidCheckDigit(code: String): Boolean {
    val digits = code.map { it - '0' }
    val sum = digits.dropLast(1).reversed().withIndex().sumOf { (index, digit) -> if (index % 2 == 0) digit * 3 else digit }
    return (10 - sum % 10) % 10 == digits.last()
}

private const val CODE_39_CHARACTERS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ-. $/+%"
private const val CODABAR_CHARACTERS = "0123456789-$:/.+ABCDabcd"
