package cz.poqet.walletka

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.ApplicationLifecycle
import cz.poqet.walletka.component.root.DefaultRootComponent
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.platform.cloudContainerFromInfoPlist
import cz.poqet.walletka.platform.iosPlatformServices
import cz.poqet.walletka.platform.walletConfigFromInfoPlist

/**
 * Entry point for the SwiftUI app: owns the root component, bound to the application lifecycle.
 * Create exactly one instance, from the `App`'s app delegate / initializer.
 *
 * [isCloudKitEntitled] says whether the app was signed with its iCloud entitlement: CloudKit traps without it
 * (e.g. unsigned Simulator builds), so the library then stays on this device.
 */
class WalletkaApp(isCloudKitEntitled: Boolean) {
    constructor() : this(isCloudKitEntitled = false)

    val root: RootComponent = DefaultRootComponent(
        componentContext = DefaultComponentContext(lifecycle = ApplicationLifecycle()),
        services = iosPlatformServices(
            walletConfigFromInfoPlist(),
            cloudContainer = cloudContainerFromInfoPlist().takeIf { isCloudKitEntitled },
        ),
    )
}
