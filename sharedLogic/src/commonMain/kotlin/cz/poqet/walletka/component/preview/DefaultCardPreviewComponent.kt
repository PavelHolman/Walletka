package cz.poqet.walletka.component.preview

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnResume
import cz.poqet.walletka.component.preview.CardPreviewComponent.PreviewModel
import cz.poqet.walletka.component.preview.CardPreviewComponent.WalletAction
import cz.poqet.walletka.component.review.toDraft
import cz.poqet.walletka.data.CardLibrary
import cz.poqet.walletka.data.StoredCard
import cz.poqet.walletka.domain.WalletFailure
import cz.poqet.walletka.platform.ImageStore
import cz.poqet.walletka.platform.WalletService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DefaultCardPreviewComponent(
    componentContext: ComponentContext,
    override val cardId: String,
    private val library: CardLibrary,
    private val walletService: WalletService,
    private val imageStore: ImageStore,
    private val onEdit: () -> Unit,
    private val onFinished: () -> Unit,
) : CardPreviewComponent, ComponentContext by componentContext {

    private val scope = coroutineScope(Dispatchers.Main.immediate)
    private val _model = MutableStateFlow<PreviewModel>(PreviewModel.Loading)
    override val model: StateFlow<PreviewModel> = _model.asStateFlow()

    private var card: StoredCard? = null
    private var isFinished = false
    private var walletStatusJob: Job? = null

    init {
        scope.launch {
            library.load()
            library.cards.collect { cards ->
                val card = cards.firstOrNull { it.id == cardId }
                // Deleted here or elsewhere (the iPad sidebar, another device through iCloud).
                if (card == null) return@collect finish()
                val isNew = this@DefaultCardPreviewComponent.card == null
                this@DefaultCardPreviewComponent.card = card
                _model.update { model ->
                    when (model) {
                        // Until the wallet answers, go by what Walletka last did with the pass.
                        PreviewModel.Loading -> PreviewModel.Showing(card.form.toForm(), card.addedToWallet, WalletAction.Idle)
                        is PreviewModel.Showing -> model.copy(form = card.form.toForm())
                    }
                }
                if (isNew) refreshWalletStatus()
            }
        }
        // The pass may have been added or removed in the Wallet app meanwhile, or by the editor.
        lifecycle.doOnResume { if (card != null) refreshWalletStatus() }
    }

    override fun onEditClicked() = onEdit()

    override fun onDeleteClicked() {
        scope.launch {
            withContext(NonCancellable) {
                library.deleteCard(cardId, { walletService.removeCard(it) }, imageStore::delete)
            }
            finish()
        }
    }

    override fun onAddToWalletClicked() {
        val card = card ?: return
        if (!startWalletAction(WalletAction.Adding)) return
        scope.launch {
            walletService.addCard(card.form.toForm().toDraft(card.walletId)).fold(
                ifLeft = { error ->
                    // Dismissing the system sheet changes nothing.
                    val action = if (error.failure == WalletFailure.CANCELLED) WalletAction.Idle else WalletAction.Failed(error.failure, error.detail)
                    _model.update { (it as? PreviewModel.Showing)?.copy(walletAction = action) ?: it }
                },
                ifRight = {
                    withContext(NonCancellable) { setAddedToWallet(true) }
                    _model.update { (it as? PreviewModel.Showing)?.copy(isInWallet = true, walletAction = WalletAction.Idle) ?: it }
                },
            )
        }
    }

    override fun onRemoveFromWalletClicked() {
        val card = card ?: return
        if (!startWalletAction(WalletAction.Removing)) return
        scope.launch {
            val removed = walletService.removeCard(card.walletId)
            val inWallet = walletService.isInWallet(card.walletId)
            // Gone either way (already gone counts too), or a wallet that can't tell: the user is asked to remove it
            // there, and Walletka stops counting it as added, so it can be added again.
            val notInWallet = removed || inWallet != true
            if (notInWallet) withContext(NonCancellable) { setAddedToWallet(false) }
            _model.update { model ->
                (model as? PreviewModel.Showing)?.copy(
                    isInWallet = model.isInWallet && !notInWallet,
                    walletAction = if (removed || inWallet == false) WalletAction.Idle else WalletAction.RemoveUnsupported,
                ) ?: model
            }
        }
    }

    override fun onWalletMessageDismissed() {
        _model.update { model ->
            if (model is PreviewModel.Showing && (model.walletAction is WalletAction.Failed || model.walletAction == WalletAction.RemoveUnsupported)) {
                model.copy(walletAction = WalletAction.Idle)
            } else {
                model
            }
        }
    }

    override fun onCloseClicked() = finish()

    /** Moves to [action] unless another wallet action is running; returns whether it did. */
    private fun startWalletAction(action: WalletAction): Boolean {
        val showing = _model.value as? PreviewModel.Showing ?: return false
        if (showing.walletAction == WalletAction.Adding || showing.walletAction == WalletAction.Removing) return false
        _model.value = showing.copy(walletAction = action)
        return true
    }

    private suspend fun setAddedToWallet(added: Boolean) {
        val current = library.get(cardId) ?: return
        if (current.addedToWallet != added) library.save(current.copy(addedToWallet = added))
    }

    private fun refreshWalletStatus() {
        val card = card ?: return
        walletStatusJob?.cancel()
        walletStatusJob = scope.launch {
            // Apple Wallet can tell; Google Wallet can't, so Walletka's own record is all there is.
            val inWallet = walletService.isInWallet(card.walletId) ?: return@launch
            _model.update { (it as? PreviewModel.Showing)?.copy(isInWallet = inWallet) ?: it }
            if (inWallet != card.addedToWallet) setAddedToWallet(inWallet)
        }
    }

    private fun finish() {
        if (isFinished) return
        isFinished = true
        onFinished()
    }
}
