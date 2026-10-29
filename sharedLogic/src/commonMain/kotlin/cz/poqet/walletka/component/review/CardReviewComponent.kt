package cz.poqet.walletka.component.review

import cz.poqet.walletka.data.LogoRegion
import cz.poqet.walletka.data.WalletSymbology
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.BannerViewport
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.WalletFailure
import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.coroutines.flow.StateFlow

/**
 * Turns one shared/pasted photo into a wallet card (analysis, suggestion, editing, issuing), or edits a card from
 * the library and re-issues it.
 */
interface CardReviewComponent {

    val model: StateFlow<Model>

    fun onNameChanged(name: String)

    fun onCodeSelected(index: Int)

    /** Edits the content of the selected code. */
    fun onCodeValueChanged(value: String)

    /** Changes the symbology of a typed code ([CardForm.isCodeTyped]); ignored for detected codes. */
    fun onCodeFormatSelected(format: CodeFormat)

    fun onColorSelected(index: Int)

    /** A colour from a colour picker; it becomes the palette's custom colour and is selected. */
    fun onCustomColorSelected(color: CardColor)

    /**
     * The user tapped the photo at the normalized point ([x], [y]) in the colour picker. Its colour is shown as
     * [Model.Editing.colorPreview] without changing the card until [onColorPreviewConfirmed].
     */
    fun onColorPreviewRequested(x: Float, y: Float)

    /** Makes the previewed colour the card's custom colour. */
    fun onColorPreviewConfirmed()

    /** Closes the colour picker without changing the card. */
    fun onColorPreviewDismissed()

    fun onBannerToggled(enabled: Boolean)

    /** The user moved/zoomed the banner crop; see [CardForm.bannerViewport] for the editor geometry. */
    fun onBannerRegionChanged(region: NormalizedRect)

    /** Turns the logo cut out of the photo on or off; see [CardForm.logo]. */
    fun onLogoToggled(enabled: Boolean)

    /** The user moved/zoomed the logo crop; see [CardForm.logoViewport] for the editor geometry. */
    fun onLogoRegionChanged(region: NormalizedRect)

    /** Edits the optional pass details (number, member name, expiry, notes). */
    fun onDetailsChanged(details: CardDetails)

    fun onAddToWalletClicked()

    /** Retries a failed automatic sync of a library card (see [Model.Editing.sync]). */
    fun onRetrySyncClicked()

    /** Re-runs the analysis after a [Model.Failed] state. */
    fun onRetryClicked()

    /** Clears a failed [Submission] so the user can edit and try again. */
    fun onSubmissionErrorDismissed()

    fun onCloseClicked()

    /** Deletes a library card (and its pass, where the wallet allows it); see [CardForm.isLibraryCard]. */
    fun onDeleteClicked()

    /** Leaves this new card for the library card it duplicates ([Model.Editing.duplicateOf]), opening its editor. */
    fun onOpenDuplicateClicked()

    sealed interface Model {
        /** [photo] is set once the image has been imported, so the UI can show what is being scanned. */
        data class Loading(val stage: LoadingStage, val photo: StoredImage?) : Model

        data class Failed(val failure: AnalysisFailure) : Model

        /**
         * [hasChanges] is `true` for a new card, and for a library card once it differs from what is in the wallet.
         */
        data class Editing(
            val form: CardForm,
            val submission: Submission,
            val hasChanges: Boolean,
            /**
             * Automatic sync of a library card's edits to the wallet; `null` for new cards and where the wallet can't
             * be updated silently (then the user saves explicitly).
             */
            val sync: SyncStatus? = null,
            /** Why the last sync failed, for [SyncStatus.FAILED]. */
            val syncError: String? = null,
            /** Colour sampled in the photo colour picker, not yet applied to the card. */
            val colorPreview: CardColor? = null,
            /** A library card with the same code, for a new card; the user can still add this one. */
            val duplicateOf: DuplicateCard? = null,
        ) : Model
    }

    enum class LoadingStage { IMPORTING, ANALYZING }

    enum class SyncStatus {
        /** The wallet shows the card as edited. */
        SYNCED,

        /** Edits are waiting for the user to pause typing. */
        PENDING,
        SYNCING,
        FAILED,

        /** The pass was removed from the wallet; adding it again needs the system sheet. */
        NOT_IN_WALLET,
    }

    data class CardForm(
        /** `null` for a card entered by hand: no banner and no photo colours then. */
        val photo: StoredImage?,
        val name: String,
        val nameSuggestions: List<String>,
        val codes: List<ScannedCode>,
        val selectedCodeIndex: Int,
        /** Suggested colours, followed by the custom colour when the user picked one. */
        val colors: List<CardColor>,
        val selectedColorIndex: Int,
        /** How many of [colors] are suggestions from the photo. */
        val suggestedColorCount: Int,
        val banner: StoredImage?,
        val isBannerEnabled: Boolean,
        /** Part of [photo] the banner is cut from; [NormalizedRect.Full] without a photo. */
        val bannerRegion: NormalizedRect,
        /** Width / height of [photo], for laying out the banner editor. */
        val photoAspectRatio: Float,
        val walletPlatform: WalletPlatform,
        /** Set once the card is in the library, i.e. it has been added to the wallet. */
        val cardId: String?,
        /**
         * The code was typed in rather than detected (no photo, or none found in it): [codes] holds that one code
         * and its format can be changed.
         */
        val isCodeTyped: Boolean = false,
        val logo: CardLogo,
        val details: CardDetails,
    ) {
        val isLibraryCard: Boolean get() = cardId != null

        val hasPhoto: Boolean get() = photo != null

        /** Formats offered for a typed code, the most common first. */
        val typedCodeFormats: List<CodeFormat> get() = TypedCodeFormats

        /**
         * [typedCodeFormats] by index, for Swift: a `List` of a Kotlin enum crosses Swift Export type-erased and can't
         * be cast back, while a single enum value can.
         */
        val typedCodeFormatCount: Int get() = TypedCodeFormats.size

        fun typedCodeFormat(index: Int): CodeFormat = TypedCodeFormats[index]

        val bannerViewport: BannerViewport get() = BannerViewport(photoAspectRatio)

        /** Editor geometry of [logo]; its shape depends on how the wallet shows logos. */
        val logoViewport: BannerViewport get() = LogoRegion.viewport(photoAspectRatio, walletPlatform)

        val selectedCode: ScannedCode get() = codes[selectedCodeIndex]

        val selectedColor: CardColor get() = colors[selectedColorIndex]

        val customColor: CardColor? get() = colors.getOrNull(suggestedColorCount)

        val isCustomColorSelected: Boolean get() = selectedColorIndex >= suggestedColorCount

        /** The symbology the wallet will render; differs from [selectedCode]'s when it has to be re-encoded. */
        val walletCodeFormat: CodeFormat
            get() = WalletSymbology.resolve(walletPlatform, selectedCode.format, selectedCode.value).displayedAs

        val isValid: Boolean get() = name.isNotBlank() && selectedCode.value.isNotBlank()
    }

    sealed interface Submission {
        data object Idle : Submission

        data object InProgress : Submission

        /**
         * The card is in the wallet; [walletUrl] opens it there (iOS only). [replacedExisting] is `true` when an
         * existing pass was updated in place rather than a new one added.
         */
        data class Added(val walletUrl: String?, val replacedExisting: Boolean) : Submission

        data class Failed(val failure: WalletFailure, val detail: String?) : Submission
    }
}

private val CommonCodeFormats = listOf(CodeFormat.QR, CodeFormat.CODE_128, CodeFormat.EAN_13, CodeFormat.PDF417, CodeFormat.AZTEC)

private val TypedCodeFormats: List<CodeFormat> = CommonCodeFormats + (CodeFormat.entries - CommonCodeFormats.toSet())
