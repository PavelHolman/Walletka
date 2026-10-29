package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CodeFormat

/**
 * Field labels written into the passes. Wallet renders them, not our UI, so they are picked here by the device
 * language when the pass is built ([forDevice]). This is simpler than Apple's per-pass `.lproj` folders (which only
 * Apple Wallet understands, would have to be signed into every pass and need the same labels as `pass.strings` in
 * each language) and works the same for Google Wallet, whose text modules have no localization of their own.
 * The pass is in the language the app was in when it was added or last updated.
 */
internal data class PassStrings(
    /** BCP 47 language of the labels, also used as the Google Wallet `language` of localized strings. */
    val languageCode: String,
    /** Front field labels (Apple shows them in small capitals; upper case keeps them consistent). */
    val member: String,
    val number: String,
    val expires: String,
    /** Back fields (Apple) and text module headers (Google). */
    val cardNumber: String,
    val memberBack: String,
    val expiresBack: String,
    val notes: String,
    val qrCode: String,
) {
    /** Label for a code shown as a front field: the symbology's name, translated where it is not a proper name. */
    fun codeLabel(format: CodeFormat): String = if (format == CodeFormat.QR) qrCode else format.displayName

    companion object {
        val English = PassStrings(
            languageCode = "en",
            member = "MEMBER",
            number = "NUMBER",
            expires = "EXPIRES",
            cardNumber = "Card number",
            memberBack = "Member",
            expiresBack = "Expires",
            notes = "Notes",
            qrCode = "QR code",
        )

        val Czech = PassStrings(
            languageCode = "cs",
            member = "ČLEN",
            number = "ČÍSLO",
            expires = "PLATNOST DO",
            cardNumber = "Číslo karty",
            memberBack = "Člen",
            expiresBack = "Platnost do",
            notes = "Poznámky",
            qrCode = "QR kód",
        )

        /**
         * The labels for a language tag or code (`cs`, `cs-CZ`, `cs_CZ`); English for anything without a
         * translation, or `null`.
         */
        fun forLanguage(language: String?): PassStrings {
            val code = language?.trim()?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return when (code) {
                "cs" -> Czech
                else -> English
            }
        }

        /** The labels in the language the app runs in. */
        fun forDevice(): PassStrings = forLanguage(deviceLanguage())
    }
}

/** The language the app's UI is shown in (its preferred localization), as a BCP 47 tag or language code. */
internal expect fun deviceLanguage(): String?
