package cz.poqet.walletka.data

/**
 * Issuer configuration. Both wallets only accept passes from a registered issuer:
 *
 * - Google Wallet: an issuer account from the Google Pay & Wallet Console, with this app's package name and
 *   signing certificate allow-listed, so unsigned pass JSON can be saved through the Android API.
 * - Apple Wallet: a Pass Type ID certificate. Passes must be signed with it, which cannot be done safely on the
 *   device, so the unsigned pass files are sent to [applePassSigningUrl] which returns the signed `.pkpass`.
 */
data class WalletConfig(
    val organizationName: String = "Walletka",
    val googleIssuerId: String? = null,
    val googleIssuerEmail: String? = null,
    val applePassTypeIdentifier: String? = null,
    val appleTeamIdentifier: String? = null,
    val applePassSigningUrl: String? = null,
) {
    val isGoogleConfigured: Boolean get() = !googleIssuerId.isNullOrBlank() && !googleIssuerEmail.isNullOrBlank()

    val isAppleConfigured: Boolean
        get() = !applePassTypeIdentifier.isNullOrBlank() && !appleTeamIdentifier.isNullOrBlank() &&
            !applePassSigningUrl.isNullOrBlank()
}
