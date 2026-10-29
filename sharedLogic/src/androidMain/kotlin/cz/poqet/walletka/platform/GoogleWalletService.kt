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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

/**
 * Issues Google Wallet generic passes.
 *
 * When the pass service has a Google Wallet issuer configured (`capabilities.googleWallet`), it creates the pass
 * object and signs the save JWT, so the app needs no issuer credentials and edits sync to the saved pass in place.
 * Otherwise the pass is saved as unsigned JSON through the on-device API (issuer ID/e-mail from `local.properties`),
 * which cannot update or remove passes afterwards.
 */
internal class GoogleWalletService(
    context: Context,
    private val config: WalletConfig,
    private val activityBridge: ActivityBridge,
    private val passService: PassServiceClient = PassServiceClient(config),
    scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : WalletService {

    private val client: PayClient = Pay.getClient(context.applicationContext)

    /** Whether the service syncs Google passes; `null` until the capabilities query has succeeded. */
    @Volatile
    private var serviceSync: Boolean? = null
    private val capabilitiesMutex = Mutex()

    init {
        // Ask early, so the review screen already knows whether library cards sync when it opens.
        if (config.isServiceConfigured) scope.launch { usesServiceSync() }
    }

    override val platform = WalletPlatform.GOOGLE_WALLET

    override suspend fun isAvailable(): Boolean = runCatching {
        client.getPayApiAvailabilityStatus(PayClient.RequestType.SAVE_PASSES).await() == PayApiAvailabilityStatus.AVAILABLE
    }.getOrDefault(false)

    override suspend fun addCard(card: CardDraft): Either<WalletError, AddedCard> = either {
        val viaService = usesServiceSync()
        ensure(viaService || config.isGoogleConfigured) {
            WalletError(WalletFailure.NOT_CONFIGURED, "Google Wallet issuer ID/e-mail missing")
        }
        ensure(isAvailable()) { WalletError(WalletFailure.UNAVAILABLE) }
        val heroImageUrl = heroImageUrl(card)
        // The service signs a JWT for an object it created with the card's walletId; the fallback is unsigned JSON.
        val jwt = if (viaService) passService.saveGooglePass(PassJson.googleServiceInput(card, config, heroImageUrl)).bind() else null
        val json = if (jwt == null) {
            PassJson.google(card, config, objectId = UUID.randomUUID().toString(), heroImageUrl = heroImageUrl)
        } else {
            null
        }
        val (resultCode, data) = activityBridge.launch { activity, requestCode ->
            if (jwt != null) client.savePassesJwt(jwt, activity, requestCode) else client.savePasses(json!!, activity, requestCode)
        } ?: raise(WalletError(WalletFailure.FAILED, "No activity attached"))
        when (resultCode) {
            Activity.RESULT_OK -> AddedCard()
            Activity.RESULT_CANCELED -> raise(WalletError(WalletFailure.CANCELLED))
            else -> raise(WalletError(WalletFailure.FAILED, data?.getStringExtra(PayClient.EXTRA_API_ERROR_MESSAGE)))
        }
    }

    /**
     * Only through the pass service (the on-device API can only add passes). Until its capabilities are known this
     * is `false`, so the review screen falls back to the explicit "Save to Google Wallet" button.
     */
    override val canUpdateInPlace: Boolean get() = serviceSync == true

    override suspend fun updateCard(card: CardDraft): Either<WalletError, AddedCard> = either {
        ensure(usesServiceSync()) { WalletError(WalletFailure.UNAVAILABLE, "Google Wallet passes can't be updated from the device") }
        passService.updateGooglePass(PassJson.googleServiceInput(card, config, heroImageUrl(card))).bind()
        AddedCard(replacedExisting = true)
    }

    /** Expires the pass through the service (Google Wallet objects can't be deleted); `false` without the service. */
    override suspend fun removeCard(walletId: String): Boolean {
        if (!usesServiceSync()) return false
        return passService.deleteGooglePass(walletId)
            .onLeft { Log.w("Walletka", "Removing the Google Wallet pass failed: ${it.detail}") }
            .getOrNull() == true
    }

    /** Asks the pass service once whether it has a Google Wallet issuer; failures are retried on the next call. */
    private suspend fun usesServiceSync(): Boolean {
        serviceSync?.let { return it }
        if (!config.isServiceConfigured) return false
        return capabilitiesMutex.withLock {
            serviceSync ?: run {
                val capabilities = passService.capabilities()
                    .onLeft { Log.w("Walletka", "Pass service capabilities unavailable: ${it.detail}") }
                    .getOrNull()
                capabilities?.googleWallet?.also { serviceSync = it } ?: false
            }
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
