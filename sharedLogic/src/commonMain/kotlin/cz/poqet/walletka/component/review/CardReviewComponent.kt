package cz.poqet.walletka.component.review

import cz.poqet.walletka.data.WalletSymbology
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.BannerViewport
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.WalletFailure
import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.coroutines.flow.StateFlow

/** Turns one shared/pasted photo into a wallet card: analysis, suggestion, editing and issuing. */
interface CardReviewComponent {

    val model: StateFlow<Model>

    fun onNameChanged(name: String)

    fun onCodeSelected(index: Int)

    fun onColorSelected(index: Int)

    fun onBannerToggled(enabled: Boolean)

    /** The user moved/zoomed the banner crop; see [CardForm.bannerViewport] for the editor geometry. */
    fun onBannerRegionChanged(region: NormalizedRect)

    fun onAddToWalletClicked()

    /** Re-runs the analysis after a [Model.Failed] state. */
    fun onRetryClicked()

    /** Clears a failed [Submission] so the user can edit and try again. */
    fun onSubmissionErrorDismissed()

    fun onCloseClicked()

    sealed interface Model {
        data class Loading(val stage: LoadingStage) : Model

        data class Failed(val failure: AnalysisFailure) : Model

        data class Editing(val form: CardForm, val submission: Submission) : Model
    }

    enum class LoadingStage { IMPORTING, ANALYZING }

    data class CardForm(
        val photo: StoredImage,
        val name: String,
        val nameSuggestions: List<String>,
        val codes: List<ScannedCode>,
        val selectedCodeIndex: Int,
        val colors: List<CardColor>,
        val selectedColorIndex: Int,
        val banner: StoredImage?,
        val isBannerEnabled: Boolean,
        /** Part of [photo] the banner is cut from. */
        val bannerRegion: NormalizedRect,
        /** Width / height of [photo], for laying out the banner editor. */
        val photoAspectRatio: Float,
        val walletPlatform: WalletPlatform,
    ) {
        val bannerViewport: BannerViewport get() = BannerViewport(photoAspectRatio)

        val selectedCode: ScannedCode get() = codes[selectedCodeIndex]

        val selectedColor: CardColor get() = colors[selectedColorIndex]

        /** The symbology the wallet will render; differs from [selectedCode]'s when it has to be re-encoded. */
        val walletCodeFormat: CodeFormat
            get() = WalletSymbology.resolve(walletPlatform, selectedCode.format, selectedCode.value).displayedAs

        val isValid: Boolean get() = name.isNotBlank()
    }

    sealed interface Submission {
        data object Idle : Submission

        data object InProgress : Submission

        data object Added : Submission

        data class Failed(val failure: WalletFailure, val detail: String?) : Submission
    }
}
