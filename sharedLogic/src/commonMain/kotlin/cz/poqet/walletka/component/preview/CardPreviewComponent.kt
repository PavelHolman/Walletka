package cz.poqet.walletka.component.preview

import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.domain.WalletFailure
import kotlinx.coroutines.flow.StateFlow

/**
 * A library card shown to be scanned (the screen brightens while it is up), with its wallet pass added or removed on
 * demand. Editing and deleting are secondary.
 */
interface CardPreviewComponent {

    /** The card shown; stable for the component's lifetime. */
    val cardId: String

    val model: StateFlow<PreviewModel>

    fun onEditClicked()

    /** Deletes the card (and its pass, where the wallet allows it) and closes the preview. */
    fun onDeleteClicked()

    fun onAddToWalletClicked()

    fun onRemoveFromWalletClicked()

    /** Clears a failed or unsupported [WalletAction]. */
    fun onWalletMessageDismissed()

    fun onCloseClicked()

    /** Not `Model`: Swift Export names a sealed type's cases after its simple name, and the editor has a `Model`. */
    sealed interface PreviewModel {
        data object Loading : PreviewModel

        data class Showing(
            /** The card as saved in the library; follows edits made in the editor. */
            val form: CardForm,
            /** The card's pass is in this device's wallet (as far as the wallet lets apps tell). */
            val isInWallet: Boolean,
            val walletAction: WalletAction,
        ) : PreviewModel
    }

    sealed interface WalletAction {
        data object Idle : WalletAction

        data object Adding : WalletAction

        data object Removing : WalletAction

        data class Failed(val failure: WalletFailure, val detail: String?) : WalletAction

        /**
         * The wallet doesn't let Walletka remove passes (Google Wallet without the pass service, or offline): the user
         * removes it there. Where the wallet can't tell whether it holds the pass, the card no longer counts as added.
         */
        data object RemoveUnsupported : WalletAction
    }
}
