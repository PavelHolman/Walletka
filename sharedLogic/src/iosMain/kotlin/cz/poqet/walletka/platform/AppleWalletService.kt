package cz.poqet.walletka.platform

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.poqet.walletka.data.ApplePassBundle
import cz.poqet.walletka.data.PassJson
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSUUID
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setHTTPBody
import platform.Foundation.setHTTPMethod
import platform.Foundation.setValue
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Issues Apple Wallet store cards: builds the pass files, has them signed by the configured signing service
 * (see [ApplePassBundle]) and presents the system "Add to Apple Wallet" sheet.
 */
internal class AppleWalletService(private val config: WalletConfig) : WalletService {

    override val platform = WalletPlatform.APPLE_WALLET

    override suspend fun isAvailable(): Boolean = PKPassLibrary.isPassLibraryAvailable() && PKAddPassesViewController.canAddPasses()

    override suspend fun addCard(card: CardDraft): Either<WalletError, Unit> = either {
        ensure(config.isAppleConfigured) {
            WalletError(WalletFailure.NOT_CONFIGURED, "Pass Type ID, Team ID or signing service URL missing")
        }
        ensure(isAvailable()) { WalletError(WalletFailure.UNAVAILABLE) }
        val files = passFiles(card)
        val signed = sign(ApplePassBundle.signingRequest(files))
        val pass = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            PKPass(data = signed, error = error.ptr).takeIf { error.value == null }
        }
        ensureNotNull(pass) { WalletError(WalletFailure.FAILED, "The signing service returned an invalid pass") }
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

    private suspend fun Raise<WalletError>.sign(body: String): NSData {
        val url = ensureNotNull(config.applePassSigningUrl?.let(NSURL::URLWithString)) {
            WalletError(WalletFailure.NOT_CONFIGURED, "Invalid signing service URL")
        }
        val request = NSMutableURLRequest.requestWithURL(url).apply {
            setHTTPMethod("POST")
            setValue("application/json", forHTTPHeaderField = "Content-Type")
            setValue("application/vnd.apple.pkpass", forHTTPHeaderField = "Accept")
            setHTTPBody(body.encodeToByteArray().toNSData())
        }
        val (data, status) = Either.catch { post(request) }
            .mapLeft { WalletError(WalletFailure.FAILED, it.message) }
            .bind()
        ensure(status in 200..299 && data != null) { WalletError(WalletFailure.FAILED, "Signing service responded $status") }
        return data
    }

    private suspend fun post(request: NSMutableURLRequest): Pair<NSData?, Long> = suspendCancellableCoroutine { continuation ->
        val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
            if (error != null) {
                continuation.resumeWithException(IllegalStateException(error.localizedDescription))
            } else {
                continuation.resume(data to ((response as? NSHTTPURLResponse)?.statusCode ?: 0L))
            }
        }
        continuation.invokeOnCancellation { task.cancel() }
        task.resume()
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
