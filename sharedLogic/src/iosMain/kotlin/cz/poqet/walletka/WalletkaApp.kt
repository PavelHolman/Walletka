package cz.poqet.walletka

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.ApplicationLifecycle
import cz.poqet.walletka.component.root.DefaultRootComponent
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.platform.iosPlatformServices
import cz.poqet.walletka.platform.walletConfigFromInfoPlist

/**
 * Entry point for the SwiftUI app: owns the root component, bound to the application lifecycle.
 * Create exactly one instance, from the `App`'s app delegate / initializer.
 */
class WalletkaApp {
    val root: RootComponent = DefaultRootComponent(
        componentContext = DefaultComponentContext(lifecycle = ApplicationLifecycle()),
        services = iosPlatformServices(walletConfigFromInfoPlist()),
    )
}
