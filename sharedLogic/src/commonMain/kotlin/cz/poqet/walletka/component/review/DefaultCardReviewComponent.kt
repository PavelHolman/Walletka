package cz.poqet.walletka.component.review

import arrow.core.raise.either
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.component.review.CardReviewComponent.LoadingStage
import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.component.review.CardReviewComponent.Submission
import cz.poqet.walletka.data.BannerCropper
import cz.poqet.walletka.data.CardSuggestion
import cz.poqet.walletka.data.ImageAnalyzer
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.platform.ImageStore
import cz.poqet.walletka.platform.WalletService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class DefaultCardReviewComponent(
    componentContext: ComponentContext,
    source: String,
    imageStore: ImageStore,
    analyzer: ImageAnalyzer,
    bannerCropper: BannerCropper,
    walletService: WalletService,
    private val onFinished: () -> Unit,
) : CardReviewComponent, ComponentContext by componentContext {

    // Survives configuration changes; the saved form additionally survives process death.
    private val store = instanceKeeper.getOrCreate {
        ReviewStore(
            source = source,
            savedForm = stateKeeper.consume(key = STATE_KEY, strategy = SavedCardForm.serializer())?.toForm(),
            imageStore = imageStore,
            analyzer = analyzer,
            bannerCropper = bannerCropper,
            walletService = walletService,
        )
    }

    override val model: StateFlow<Model> = store.model

    init {
        stateKeeper.register(key = STATE_KEY, strategy = SavedCardForm.serializer()) {
            (store.model.value as? Model.Editing)?.form?.let(SavedCardForm::of)
        }
    }

    override fun onNameChanged(name: String) = store.updateForm { copy(name = name) }

    override fun onCodeSelected(index: Int) = store.updateForm { copy(selectedCodeIndex = index.coerceIn(codes.indices)) }

    override fun onColorSelected(index: Int) = store.updateForm { copy(selectedColorIndex = index.coerceIn(colors.indices)) }

    override fun onBannerToggled(enabled: Boolean) = store.updateForm { copy(isBannerEnabled = enabled && banner != null) }

    override fun onBannerRegionChanged(region: NormalizedRect) = store.updateBannerRegion(region)

    override fun onAddToWalletClicked() = store.addToWallet()

    override fun onRetryClicked() = store.analyze()

    override fun onSubmissionErrorDismissed() = store.dismissSubmissionError()

    override fun onCloseClicked() = onFinished()

    private companion object {
        const val STATE_KEY = "card_form"
    }
}

private class ReviewStore(
    private val source: String,
    savedForm: CardForm?,
    private val imageStore: ImageStore,
    private val analyzer: ImageAnalyzer,
    private val bannerCropper: BannerCropper,
    private val walletService: WalletService,
) : InstanceKeeper.Instance {

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _model = MutableStateFlow<Model>(
        savedForm?.let { Model.Editing(it, Submission.Idle) } ?: Model.Loading(LoadingStage.IMPORTING),
    )
    val model: StateFlow<Model> = _model.asStateFlow()
    private var job: Job? = null
    private var bannerJob: Job? = null

    init {
        if (savedForm == null) analyze()
    }

    fun analyze() {
        job?.cancel()
        job = scope.launch {
            _model.value = Model.Loading(LoadingStage.IMPORTING)
            either {
                val image = imageStore.import(source).bind()
                _model.value = Model.Loading(LoadingStage.ANALYZING)
                analyzer.analyze(image).bind()
            }.fold(
                ifLeft = { _model.value = Model.Failed(it.failure) },
                ifRight = { _model.value = Model.Editing(it.toForm(), Submission.Idle) },
            )
        }
    }

    fun updateForm(update: CardForm.() -> CardForm) {
        _model.update { model ->
            if (model is Model.Editing && model.submission !is Submission.InProgress) {
                model.copy(form = model.form.update())
            } else {
                model
            }
        }
    }

    /** Moves the crop immediately and re-cuts the banner in the background, keeping only the latest result. */
    fun updateBannerRegion(region: NormalizedRect) {
        val editing = _model.value as? Model.Editing ?: return
        val clamped = editing.form.bannerViewport.clamp(region)
        if (clamped == editing.form.bannerRegion) return
        updateForm { copy(bannerRegion = clamped) }
        bannerJob?.cancel()
        bannerJob = scope.launch {
            delay(BANNER_RECROP_DEBOUNCE)
            val banner = bannerCropper.crop(editing.form.photo, clamped).getOrNull() ?: return@launch
            val current = _model.value as? Model.Editing
            if (current == null || current.form.bannerRegion != clamped) {
                imageStore.delete(banner) // superseded by a newer crop
                return@launch
            }
            _model.value = current.copy(form = current.form.copy(banner = banner, isBannerEnabled = true))
            current.form.banner?.let { imageStore.delete(it) }
        }
    }

    fun addToWallet() {
        val editing = _model.value as? Model.Editing ?: return
        if (!editing.form.isValid || editing.submission == Submission.InProgress) return
        // Use the banner for the final crop, even if a re-crop is still pending.
        if (bannerJob?.isActive == true) {
            scope.launch {
                bannerJob?.join()
                addToWallet()
            }
            return
        }
        _model.value = editing.copy(submission = Submission.InProgress)
        job = scope.launch {
            val submission = walletService.addCard(editing.form.toDraft()).fold(
                ifLeft = { Submission.Failed(it.failure, it.detail) },
                ifRight = { Submission.Added },
            )
            _model.update { (it as? Model.Editing)?.copy(submission = submission) ?: it }
        }
    }

    fun dismissSubmissionError() {
        _model.update { model ->
            if (model is Model.Editing && model.submission is Submission.Failed) model.copy(submission = Submission.Idle) else model
        }
    }

    private fun CardSuggestion.toForm() = CardForm(
        photo = image,
        name = names.firstOrNull().orEmpty(),
        nameSuggestions = names,
        codes = codes,
        selectedCodeIndex = 0,
        colors = colors,
        selectedColorIndex = 0,
        banner = banner,
        isBannerEnabled = banner != null,
        bannerRegion = bannerRegion,
        photoAspectRatio = photoAspectRatio,
        walletPlatform = walletService.platform,
    )

    private fun CardForm.toDraft() = CardDraft(
        name = name.trim(),
        code = selectedCode,
        backgroundColor = selectedColor,
        banner = banner.takeIf { isBannerEnabled },
    )

    private companion object {
        const val BANNER_RECROP_DEBOUNCE = 250L
    }

    override fun onDestroy() {
        scope.cancel()
        (_model.value as? Model.Editing)?.form?.let { form ->
            CoroutineScope(Dispatchers.Main).launch {
                withContext(NonCancellable) {
                    imageStore.delete(form.photo)
                    form.banner?.let { imageStore.delete(it) }
                }
            }
        }
    }
}
