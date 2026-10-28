package cz.poqet.walletka.platform

import cz.poqet.walletka.data.WalletConfig
import platform.Foundation.NSBundle

internal fun iosPlatformServices(config: WalletConfig): PlatformServices = PlatformServices(
    imageStore = IosImageStore(),
    barcodeDetector = VisionBarcodeDetector(),
    textRecognizer = VisionTextRecognizer(),
    imageProcessor = IosImageProcessor(),
    walletService = AppleWalletService(config),
)

/** Reads the issuer configuration from the app's Info.plist (filled from `Config.xcconfig`). */
internal fun walletConfigFromInfoPlist(bundle: NSBundle = NSBundle.mainBundle): WalletConfig {
    fun value(key: String) = (bundle.objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() }
    return WalletConfig(
        organizationName = value("WalletkaOrganizationName") ?: "Walletka",
        applePassTypeIdentifier = value("WalletkaPassTypeIdentifier"),
        appleTeamIdentifier = value("WalletkaTeamIdentifier"),
        applePassSigningUrl = value("WalletkaPassSigningURL"),
    )
}
