package cz.poqet.walletka.platform

import cz.poqet.walletka.data.WalletConfig
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

internal fun iosPlatformServices(config: WalletConfig): PlatformServices = PlatformServices(
    imageStore = IosImageStore(),
    barcodeDetector = VisionBarcodeDetector(),
    textRecognizer = VisionTextRecognizer(),
    imageProcessor = IosImageProcessor(),
    walletService = AppleWalletService(config),
    dataDirectory = applicationSupportDirectory(),
)

private fun applicationSupportDirectory(): String {
    val base = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true).first() as String
    return "$base/Walletka".also {
        NSFileManager.defaultManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null)
    }
}

/** Reads the issuer configuration from the app's Info.plist (filled from `Config.xcconfig`). */
internal fun walletConfigFromInfoPlist(bundle: NSBundle = NSBundle.mainBundle): WalletConfig {
    fun value(key: String) = (bundle.objectForInfoDictionaryKey(key) as? String)?.takeIf { it.isNotBlank() }
    return WalletConfig(
        organizationName = value("WalletkaOrganizationName") ?: "Walletka",
        serviceUrl = value("WalletkaServiceURL"),
        serviceApiKey = value("WalletkaServiceAPIKey"),
    )
}
