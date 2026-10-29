package cz.poqet.walletka.platform

import android.content.Context
import com.arkivanov.decompose.ComponentContext
import cz.poqet.walletka.component.root.DefaultRootComponent
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.data.WalletConfig

/** Android composition root: wires the ML Kit / Google Wallet implementations into the shared components. */
fun createRootComponent(
    componentContext: ComponentContext,
    context: Context,
    config: WalletConfig,
    activityBridge: ActivityBridge,
): RootComponent = DefaultRootComponent(
    componentContext = componentContext,
    services = PlatformServices(
        imageStore = AndroidImageStore(context.applicationContext),
        barcodeDetector = MlKitBarcodeDetector(),
        textRecognizer = MlKitTextRecognizer(),
        imageProcessor = AndroidImageProcessor(),
        walletService = GoogleWalletService(context, config, activityBridge),
        dataDirectory = context.filesDir.path,
    ),
)
