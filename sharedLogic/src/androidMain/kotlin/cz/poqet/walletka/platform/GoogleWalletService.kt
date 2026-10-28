package cz.poqet.walletka.platform

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.google.android.gms.pay.Pay
import com.google.android.gms.pay.PayApiAvailabilityStatus
import com.google.android.gms.pay.PayClient
import cz.poqet.walletka.data.PassJson
import cz.poqet.walletka.data.PassServiceClient
import cz.poqet.walletka.data.WalletConfig
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.WalletError
import cz.poqet.walletka.domain.WalletFailure
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * Connects [GoogleWalletService] to the hosting activity: `PayClient.savePasses` needs an [Activity] and
 * reports back through `onActivityResult`.
 */
class ActivityBridge {
    internal var activity: Activity? = null
        private set
    private var pending: CompletableDeferred<Pair<Int, Intent?>>? = null

    fun attach(activity: Activity) {
        this.activity = activity
    }

    fun detach(activity: Activity) {
        if (this.activity === activity) this.activity = null
    }

    /** Forward from `Activity.onActivityResult`; returns `true` if the result was consumed. */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode != REQUEST_CODE) return false
        pending?.complete(resultCode to data)
        pending = null
        return true
    }

    internal suspend fun launch(start: (Activity, Int) -> Unit): Pair<Int, Intent?>? {
        val activity = activity ?: return null
        val deferred = CompletableDeferred<Pair<Int, Intent?>>()
        pending?.cancel()
        pending = deferred
        start(activity, REQUEST_CODE)
        return deferred.await()
    }

    private companion object {
        const val REQUEST_CODE = 0x57A1
    }
}

internal class GoogleWalletService(
    context: Context,
    private val config: WalletConfig,
    private val activityBridge: ActivityBridge,
    private val passService: PassServiceClient = PassServiceClient(config),
) : WalletService {

    private val client: PayClient = Pay.getClient(context.applicationContext)

    override val platform = WalletPlatform.GOOGLE_WALLET

    override suspend fun isAvailable(): Boolean = runCatching {
        client.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES).await() == PayApiAvailabilityStatus.AVAILABLE
    }.getOrDefault(false)

    override suspend fun addCard(card: CardDraft): Either<WalletError, Unit> = either {
        ensure(config.isGoogleConfigured) { WalletError(WalletFailure.NOT_CONFIGURED, "Google Wallet issuer ID/e-mail missing") }
        ensure(isAvailable()) { WalletError(WalletFailure.UNAVAILABLE) }
        val json = PassJson.google(card, config, objectId = UUID.randomUUID().toString(), heroImageUrl = heroImageUrl(card))
        val (resultCode, data) = activityBridge.launch { activity, requestCode -> client.savePasses(json, activity, requestCode) }
            ?: raise(WalletError(WalletFailure.FAILED, "No activity attached"))
        when (resultCode) {
            Activity.RESULT_OK -> Unit
            Activity.RESULT_CANCELED -> raise(WalletError(WalletFailure.CANCELLED))
            else -> raise(WalletError(WalletFailure.FAILED, data?.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE)))
        }
    }

    /**
     * Google Wallet only takes images by public URL, so the banner is uploaded to the pass service first. The banner
     * is decoration: without a service, or if the upload fails, the card is saved without it.
     */
    private suspend fun heroImageUrl(card: CardDraft): String? {
        val banner = card.banner?.takeIf { config.isServiceConfigured } ?: return null
        val png = withContext(Dispatchers.IO) { runCatching { File(banner.path).readBytes() }.getOrNull() } ?: return null
        return passService.uploadBanner(png).onLeft { Log.w("Walletka", "Banner upload failed: ${it.detail}") }.getOrNull()
    }
}
