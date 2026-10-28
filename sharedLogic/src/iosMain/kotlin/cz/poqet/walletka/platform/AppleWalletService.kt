package cz.poqet.walletka.platform

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.poqet.walletka.data.PassJson
import cz.poqet.walletka.data.PassServiceClient
import cz.poqet.walletka.data.WalletConfig
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.WalletError
import cz.poqet.walletka.domain.WalletFailure
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSUUID
import platform.PassKit.PKAddPassesViewController
import platform.PassKit.PKAddPassesViewControllerDelegateProtocol
import platform.PassKit.PKPass
import platform.PassKit.PKPassLibrary
import platform.UIKit.UIApplication
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImagePNGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UIViewController
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject

/**
 * Issues Apple Wallet store cards: builds the pass files, has them signed by the Walletka pass service and
 * presents the system "Add to Apple Wallet" sheet.
 */
internal class AppleWalletService(
    private val config: WalletConfig,
    private val passService: PassServiceClient = PassServiceClient(config),
) : WalletService {

    override val platform = WalletPlatform.APPLE_WALLET

    override suspend fun isAvailable(): Boolean = PKPassLibrary.isPassLibraryAvailable() && PKAddPassesViewController.canAddPasses()

    override suspend fun addCard(card: CardDraft): Either<WalletError, Unit> = either {
        ensure(config.isServiceConfigured) { WalletError(WalletFailure.NOT_CONFIGURED, "Pass service URL missing") }
        ensure(isAvailable()) { WalletError(WalletFailure.UNAVAILABLE) }
        val signed = passService.signApplePass(passFiles(card)).bind().toNSData()
        val pass = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            // A pass PassKit rejects (e.g. not signed by an Apple-issued certificate) makes the initializer return nil,
            // which Kotlin/Native reports as an exception.
            val pass = runCatching { PKPass(data = signed, error = error.ptr) }.getOrNull()
            ensure(pass != null && error.value == null) {
                WalletError(WalletFailure.FAILED, error.value?.localizedDescription ?: "PassKit rejected the signed pass")
            }
            pass
        }
        ensure(present(pass)) { WalletError(WalletFailure.CANCELLED) }
    }

    private suspend fun passFiles(card: CardDraft): Map<String, ByteArray> = withContext(Dispatchers.Default) {
        buildMap {
            put("pass.json", PassJson.apple(card, config, serialNumber = NSUUID().UUIDString).encodeToByteArray())
            put("icon.png", solidPng(card.backgroundColor, size = 29.0, scale = 1.0))
            put("icon@2x.png", solidPng(card.backgroundColor, size = 29.0, scale = 2.0))
            put("icon@3x.png", solidPng(card.backgroundColor, size = 29.0, scale = 3.0))
            card.banner?.let { banner ->
                NSFileManager.defaultManager.contentsAtPath(banner.path)?.let { put("strip@3x.png", it.toByteArray()) }
            }
        }
    }

    /** Presents the add-pass sheet and returns whether the pass ended up in the library. */
    private suspend fun present(pass: PKPass): Boolean = withContext(Dispatchers.Main) {
        val controller = PKAddPassesViewController(pass = pass)
        val presenter = topViewController() ?: return@withContext false
        val finished = CompletableDeferred<Unit>()
        val delegate = AddPassDelegate { finished.complete(Unit) }
        controller.delegate = delegate
        presenter.presentViewController(controller, animated = true, completion = null)
        finished.await()
        PKPassLibrary().containsPass(pass)
    }

    private fun topViewController(): UIViewController? {
        val scene = UIApplication.sharedApplication.connectedScenes
            .filterIsInstance<UIWindowScene>()
            .firstOrNull { it.activationState == UISceneActivationStateForegroundActive }
        var controller = scene?.keyWindow?.rootViewController
        while (controller?.presentedViewController != null) controller = controller.presentedViewController
        return controller
    }

    private fun solidPng(color: CardColor, size: Double, scale: Double): ByteArray {
        val format = UIGraphicsImageRendererFormat().apply { this.scale = scale }
        val image = UIGraphicsImageRenderer(size = CGSizeMake(size, size), format = format).imageWithActions {
            UIColor.colorWithRed(color.red / 255.0, color.green / 255.0, color.blue / 255.0, 1.0).setFill()
            UIRectFill(CGRectMake(0.0, 0.0, size, size))
        }
        return UIImagePNGRepresentation(image)?.toByteArray() ?: ByteArray(0)
    }

    private class AddPassDelegate(private val onFinish: () -> Unit) : NSObject(), PKAddPassesViewControllerDelegateProtocol {
        override fun addPassesViewControllerDidFinish(controller: PKAddPassesViewController) {
            controller.dismissViewControllerAnimated(true) { onFinish() }
        }
    }
}
