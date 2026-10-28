package cz.poqet.walletka.data

/**
 * Issuer configuration. Both wallets only accept passes from a registered issuer:
 *
 * - Google Wallet: an issuer account from the Google Pay & Wallet Console, with this app's package name and
 *   signing certificate allow-listed, so unsigned pass JSON can be saved through the Android API.
 * - Apple Wallet: a Pass Type ID certificate. Passes must be signed with it, which cannot be done safely on the
 *   device, so they are signed by the Walletka pass service (`:passService`, a Cloudflare Worker).
 *
 * The pass service also hosts banners, which Google Wallet only accepts as public image URLs.
 */
data class WalletConfig(
    val organizationName: String = "Walletka",
    val googleIssuerId: String? = null,
    val googleIssuerEmail: String? = null,
    /** Base URL of the pass service, e.g. `https://walletka-pass-service.<account>.workers.dev`. */
    val serviceUrl: String? = null,
    /** Sent as `Authorization: Bearer <key>`; must match the service's `API_KEY` secret. */
    val serviceApiKey: String? = null,
) {
    val isGoogleConfigured: Boolean get() = !googleIssuerId.isNullOrBlank() && !googleIssuerEmail.isNullOrBlank()

    val isServiceConfigured: Boolean get() = !serviceUrl.isNullOrBlank()
}
