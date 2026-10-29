package cz.poqet.walletka.data

import java.util.Locale

/** The default locale, which Android sets to the app's (per-app or system) language. */
internal actual fun deviceLanguage(): String? = Locale.getDefault().toLanguageTag()
