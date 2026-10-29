package cz.poqet.walletka.domain

/**
 * Optional text shown on the pass besides the code: on the back of an Apple pass (number and name also on the
 * front), as text modules on a Google pass. Blank values are left out of the pass.
 */
data class CardDetails(
    /** Card or member number as text, often the code's value. */
    val memberNumber: String,
    val memberName: String,
    /** ISO 8601 calendar date (`2027-05-31`), or blank; see [formatDate] / [parseDate]. */
    val expiryDate: String,
    val notes: String,
) {
    /** The expiry as days since 1970-01-01, or `null` when it is blank or not a valid date. */
    val expiryEpochDay: Long? get() = parseDate(expiryDate)

    val isEmpty: Boolean
        get() = memberNumber.isBlank() && memberName.isBlank() && expiryEpochDay == null && notes.isBlank()

    /** Without surrounding whitespace and with an invalid expiry dropped: what ends up in the pass. */
    fun trimmed(): CardDetails = CardDetails(
        memberNumber = memberNumber.trim(),
        memberName = memberName.trim(),
        expiryDate = expiryEpochDay?.let(::formatDate).orEmpty(),
        notes = notes.trim(),
    )

    companion object {
        val Empty = CardDetails(memberNumber = "", memberName = "", expiryDate = "", notes = "")

        private val isoDate = Regex("""(\d{4})-(\d{2})-(\d{2})""")

        /** Days since 1970-01-01 as an ISO 8601 date (proleptic Gregorian calendar). */
        fun formatDate(epochDay: Long): String {
            // Howard Hinnant's civil_from_days.
            val z = epochDay + 719_468
            val era = (if (z >= 0) z else z - 146_096) / 146_097
            val doe = z - era * 146_097
            val yoe = (doe - doe / 1_460 + doe / 36_524 - doe / 146_096) / 365
            val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
            val mp = (5 * doy + 2) / 153
            val day = doy - (153 * mp + 2) / 5 + 1
            val month = if (mp < 10) mp + 3 else mp - 9
            val year = yoe + era * 400 + if (month <= 2) 1 else 0
            return "${year.toString().padStart(4, '0')}-${month.toString().padStart(2, '0')}-${day.toString().padStart(2, '0')}"
        }

        /** Parses an ISO 8601 date (`2027-05-31`) into days since 1970-01-01; `null` if it is not a valid date. */
        fun parseDate(date: String): Long? {
            val text = date.trim()
            val (y, m, d) = isoDate.matchEntire(text)?.destructured ?: return null
            val year = y.toLong()
            val month = m.toLong()
            val day = d.toLong()
            if (month !in 1..12 || day !in 1..31) return null
            // Howard Hinnant's days_from_civil.
            val shifted = if (month <= 2) year - 1 else year
            val era = (if (shifted >= 0) shifted else shifted - 399) / 400
            val yoe = shifted - era * 400
            val doy = (153 * (if (month > 2) month - 3 else month + 9) + 2) / 5 + day - 1
            val doe = yoe * 365 + yoe / 4 - yoe / 100 + doy
            val epochDay = era * 146_097 + doe - 719_468
            // Rejects days past the end of the month (e.g. 2027-02-30), which the formula would roll over.
            return epochDay.takeIf { formatDate(it) == text }
        }
    }
}
