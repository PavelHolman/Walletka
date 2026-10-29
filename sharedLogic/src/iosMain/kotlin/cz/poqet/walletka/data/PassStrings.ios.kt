package cz.poqet.walletka.data

import platform.Foundation.NSBundle
import platform.Foundation.NSLocale
import platform.Foundation.preferredLanguages

/**
 * The localization the app bundle resolved for the user (honours the per-app language in Settings), falling back
 * to the first preferred system language.
 */
internal actual fun deviceLanguage(): String? =
    NSBundle.mainBundle.preferredLocalizations.firstOrNull() as? String
        ?: NSLocale.preferredLanguages.firstOrNull() as? String
