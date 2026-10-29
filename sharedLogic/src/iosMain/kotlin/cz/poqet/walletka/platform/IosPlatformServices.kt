package cz.poqet.walletka.platform

import cz.poqet.walletka.data.WalletConfig
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

/** [cloudContainer] is the iCloud container for syncing the library; `null` keeps it on this device. */
internal fun iosPlatformServices(config: WalletConfig, cloudContainer: String? = null): PlatformServices {
    val dataDirectory = applicationSupportDirectory()
    val imageStore = IosImageStore()
    return PlatformServices(
        imageStore = imageStore,
        barcodeDetector = VisionBarcodeDetector(),
        textRecognizer = VisionTextRecognizer(),
        imageProcessor = IosImageProcessor(),
        walletService = AppleWalletService(config),
        dataDirectory = dataDirectory,
        librarySync = cloudContainer?.let { CloudKitLibrarySync(it, stateDirectory = "$dataDirectory/cloudkit") },
        cardDetector = IosCardDetector(imageStore),
    )
}

/** The app's iCloud container (`WalletkaCloudContainer` in Info.plist), if it has one. */
internal fun cloudContainerFromInfoPlist(bundle: NSBundle = NSBundle.mainBundle): String? =
    (bundle.objectForInfoDictionaryKey("WalletkaCloudContainer") as? String)?.takeIf { it.startsWith("iCloud.") }

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
