package cz.poqet.walletka.platform

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.poqet.walletka.data.PassJson
import cz.poqet.walletka.data.PassStrings
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
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
import platform.UIKit.UIImage
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

    /** Strong reference to the delegate of the add-pass sheet currently shown (see [present]). */
    private var activeDelegate: AddPassDelegate? = null

    override val canUpdateInPlace = true

    override suspend fun updateCard(card: CardDraft): Either<WalletError, AddedCard> = either {
        ensure(config.isServiceConfigured) { WalletError(WalletFailure.NOT_CONFIGURED, "Pass service URL missing") }
        val pass = signedPass(card).bind()
        val library = PKPassLibrary()
        ensure(library.containsPass(pass)) { WalletError(WalletFailure.NOT_IN_WALLET) }
        ensure(withContext(Dispatchers.Main) { library.replacePassWithPass(pass) }) {
            WalletError(WalletFailure.FAILED, "Apple Wallet did not accept the updated pass")
        }
        AddedCard(walletUrl = pass.passURL?.absoluteString, replacedExisting = true)
    }

    /** Asked once: both calls wait for the system's Wallet service, which can take seconds right after boot. */
    private var available: Boolean? = null

    override suspend fun isAvailable(): Boolean = available ?: withContext(Dispatchers.Default) {
        // Off the main thread: at launch the app would otherwise show a blank screen until the Wallet service answers.
        PKPassLibrary.isPassLibraryAvailable() && PKAddPassesViewController.canAddPasses()
    }.also { available = it }

    override suspend fun addCard(card: CardDraft): Either<WalletError, AddedCard> = either {
        ensure(config.isServiceConfigured) { WalletError(WalletFailure.NOT_CONFIGURED, "Pass service URL missing") }
        ensure(isAvailable()) { WalletError(WalletFailure.UNAVAILABLE) }
        val pass = signedPass(card).bind()
        // A library card edited later keeps its serial number, so its pass can be replaced without any UI. Where Wallet
        // won't (it knows the pass but no longer holds it), the add sheet still can.
        val library = PKPassLibrary()
        if (library.containsPass(pass) && withContext(Dispatchers.Main) { library.replacePassWithPass(pass) }) {
            return@either AddedCard(walletUrl = pass.passURL?.absoluteString, replacedExisting = true)
        }
        ensure(present(pass)) { WalletError(WalletFailure.CANCELLED) }
        AddedCard(walletUrl = pass.passURL?.absoluteString)
    }

    override suspend fun removeCard(walletId: String): Boolean = withContext(Dispatchers.Main) {
        val library = PKPassLibrary()
        val pass = library.passes().filterIsInstance<PKPass>().firstOrNull { it.serialNumber == walletId }
            ?: return@withContext false
        library.removePass(pass)
        true
    }

    // Reading the pass library is a synchronous call into the Wallet service; keep it off the main thread.
    override suspend fun isInWallet(walletId: String): Boolean? = withContext(Dispatchers.Default) {
        if (!PKPassLibrary.isPassLibraryAvailable()) return@withContext null
        PKPassLibrary().passes().filterIsInstance<PKPass>().any { it.serialNumber == walletId }
    }

    /** Signs the pass through the pass service and parses it with PassKit. */
    private suspend fun signedPass(card: CardDraft): Either<WalletError, PKPass> = either {
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
        pass
    }

    private suspend fun passFiles(card: CardDraft): Map<String, ByteArray> = withContext(Dispatchers.Default) {
        buildMap {
            put("pass.json", PassJson.apple(card, config, serialNumber = card.walletId, strings = PassStrings.forDevice()).encodeToByteArray())
            put("icon.png", solidPng(card.backgroundColor, size = 29.0, scale = 1.0))
            put("icon@2x.png", solidPng(card.backgroundColor, size = 29.0, scale = 2.0))
            put("icon@3x.png", solidPng(card.backgroundColor, size = 29.0, scale = 3.0))
            card.banner?.let { banner ->
                NSFileManager.defaultManager.contentsAtPath(banner.path)?.let { put("strip@3x.png", it.toByteArray()) }
            }
            card.logo?.let { logo ->
                UIImage.imageWithContentsOfFile(logo.path)?.let { image ->
                    for ((name, scale) in listOf("logo.png" to 1.0, "logo@2x.png" to 2.0, "logo@3x.png" to 3.0)) {
                        scaledPng(image, width = LOGO_WIDTH, scale = scale)?.let { put(name, it) }
                    }
                }
            }
        }
    }

    /** [image] drawn [width] points wide (keeping its aspect ratio) at [scale]. */
    private fun scaledPng(image: UIImage, width: Double, scale: Double): ByteArray? {
        val (imageWidth, imageHeight) = image.size.useContents { width to height }
        if (imageWidth <= 0.0 || imageHeight <= 0.0) return null
        val height = width * imageHeight / imageWidth
        val format = UIGraphicsImageRendererFormat().apply { this.scale = scale }
        val scaled = UIGraphicsImageRenderer(size = CGSizeMake(width, height), format = format).imageWithActions {
            image.drawInRect(CGRectMake(0.0, 0.0, width, height))
        }
        return UIImagePNGRepresentation(scaled)?.toByteArray()
    }

    /** Presents the add-pass sheet and returns whether the pass ended up in the library. */
    private suspend fun present(pass: PKPass): Boolean = withContext(Dispatchers.Main) {
        val controller = PKAddPassesViewController(pass = pass)
        val presenter = topViewController() ?: return@withContext false
        val finished = CompletableDeferred<Unit>()
        // PassKit holds its delegate weakly; keep it reachable until the sheet is gone, or Kotlin/Native's GC may
        // collect it while we wait and the sheet's outcome is never reported.
        val delegate = AddPassDelegate { finished.complete(Unit) }
        activeDelegate = delegate
        controller.delegate = delegate
        presenter.presentViewController(controller, animated = true, completion = null)
        // Safety net: if the sheet disappears without a delegate callback, don't wait forever.
        val watchdog = launch {
            delay(1_000)
            while (controller.presentingViewController != null || controller.isBeingPresented()) delay(300)
            finished.complete(Unit)
        }
        try {
            finished.await()
        } finally {
            watchdog.cancel()
            activeDelegate = null
        }
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

    private companion object {
        /** Apple Wallet logos are at most 160x50 points; the logo crop has exactly that shape. */
        const val LOGO_WIDTH = 160.0
    }

    private class AddPassDelegate(private val onFinish: () -> Unit) : NSObject(), PKAddPassesViewControllerDelegateProtocol {
        override fun addPassesViewControllerDidFinish(controller: PKAddPassesViewController) {
            // Report right away: the outcome is known now, and waiting for the dismissal animation's completion is
            // not reliable when PassKit dismisses the sheet itself.
            onFinish()
            if (controller.presentingViewController != null) controller.dismissViewControllerAnimated(true, completion = null)
        }
    }
}
