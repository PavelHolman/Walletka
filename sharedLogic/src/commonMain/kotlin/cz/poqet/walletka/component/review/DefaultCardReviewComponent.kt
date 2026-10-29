package cz.poqet.walletka.component.review

import arrow.core.raise.either
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.arkivanov.essenty.instancekeeper.getOrCreate
import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.component.review.CardReviewComponent.LoadingStage
import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.component.review.CardReviewComponent.Submission
import cz.poqet.walletka.component.review.CardReviewComponent.SyncStatus
import cz.poqet.walletka.data.BannerCropper
import cz.poqet.walletka.data.CardLibrary
import cz.poqet.walletka.data.CardSuggestion
import cz.poqet.walletka.data.ColorPalette
import cz.poqet.walletka.data.ImageAnalyzer
import cz.poqet.walletka.data.StoredCard
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.WalletFailure
import cz.poqet.walletka.platform.ImageProcessor
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
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** What the review edits: a new photo, or a card from the library. */
internal sealed interface ReviewSource {
    /** See [ImageStore.import] for [source]. */
    data class Photo(val source: String) : ReviewSource

    data class LibraryCard(val cardId: String) : ReviewSource
}

/** Collaborators of [DefaultCardReviewComponent], shared by all instances. */
internal class ReviewDependencies(
    val imageStore: ImageStore,
    val imageProcessor: ImageProcessor,
    val analyzer: ImageAnalyzer,
    val bannerCropper: BannerCropper,
    val walletService: WalletService,
    val library: CardLibrary,
)

internal class DefaultCardReviewComponent(
    componentContext: ComponentContext,
    source: ReviewSource,
    dependencies: ReviewDependencies,
    private val onFinished: () -> Unit,
) : CardReviewComponent, ComponentContext by componentContext {

    // Survives configuration changes; the saved form additionally survives process death.
    private val store = instanceKeeper.getOrCreate {
        ReviewStore(
            source = source,
            savedForm = stateKeeper.consume(key = STATE_KEY, strategy = SavedCardForm.serializer())?.toForm(),
            dependencies = dependencies,
            onFinished = onFinished,
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

    override fun onCodeValueChanged(value: String) = store.updateForm {
        copy(codes = codes.mapIndexed { index, code -> if (index == selectedCodeIndex) code.copy(value = value) else code })
    }

    override fun onColorSelected(index: Int) = store.updateForm { copy(selectedColorIndex = index.coerceIn(colors.indices)) }

    override fun onCustomColorSelected(color: CardColor) = store.updateForm { withCustomColor(color) }

    override fun onColorPreviewRequested(x: Float, y: Float) = store.previewPhotoColor(x, y)

    override fun onColorPreviewConfirmed() = store.confirmColorPreview()

    override fun onColorPreviewDismissed() = store.setColorPreview(null)

    override fun onBannerToggled(enabled: Boolean) = store.updateForm { copy(isBannerEnabled = enabled && banner != null) }

    override fun onBannerRegionChanged(region: NormalizedRect) = store.updateBannerRegion(region)

    override fun onAddToWalletClicked() = store.addToWallet()

    override fun onRetryClicked() = store.load()

    override fun onSubmissionErrorDismissed() = store.dismissSubmissionError()

    override fun onRetrySyncClicked() = store.syncNow()

    override fun onCloseClicked() = store.close()

    override fun onDeleteClicked() = store.delete()

    private companion object {
        const val STATE_KEY = "card_form"
    }
}

private class ReviewStore(
    private val source: ReviewSource,
    savedForm: CardForm?,
    private val dependencies: ReviewDependencies,
    private val onFinished: () -> Unit,
) : InstanceKeeper.Instance {

    private val imageStore get() = dependencies.imageStore
    private val library get() = dependencies.library

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _model = MutableStateFlow<Model>(
        savedForm?.let { Model.Editing(it, Submission.Idle, hasChanges = true) } ?: Model.Loading(LoadingStage.IMPORTING, photo = null),
    )
    val model: StateFlow<Model> = _model.asStateFlow()
    private var job: Job? = null
    private var bannerJob: Job? = null

    /** The form as it is in the wallet; `null` while the card has never been added. */
    private var issuedForm: CardForm? = null

    private var syncJob: Job? = null

    /** Set when the screen closed with edits still to sync; the sync then owns the working images. */
    private var isFlushingOnClose = false

    /** Library cards are kept in sync with the wallet automatically where the wallet allows silent updates. */
    private fun autoSyncs(form: CardForm) = form.isLibraryCard && dependencies.walletService.canUpdateInPlace

    init {
        if (savedForm == null) {
            load()
        } else if (savedForm.cardId != null) {
            // Restored after process death: compare against the library version again.
            scope.launch { issuedForm = library.get(savedForm.cardId)?.form?.toForm(); refreshChanges() }
        }
    }

    fun load() {
        job?.cancel()
        job = scope.launch {
            _model.value = Model.Loading(LoadingStage.IMPORTING, photo = null)
            when (source) {
                is ReviewSource.Photo -> either {
                    val image = imageStore.import(source.source).bind()
                    _model.value = Model.Loading(LoadingStage.ANALYZING, photo = image)
                    dependencies.analyzer.analyze(image).bind()
                }.fold(
                    ifLeft = { _model.value = Model.Failed(it.failure) },
                    ifRight = { _model.value = Model.Editing(it.toForm(), Submission.Idle, hasChanges = true) },
                )

                is ReviewSource.LibraryCard -> {
                    val form = library.get(source.cardId)?.form?.toForm()
                    issuedForm = form
                    _model.value = form?.let {
                        Model.Editing(it, Submission.Idle, hasChanges = false, sync = SyncStatus.SYNCED.takeIf { _ -> autoSyncs(it) })
                    }
                        ?: Model.Failed(AnalysisFailure.IMAGE_UNREADABLE)
                }
            }
        }
    }

    fun updateForm(update: CardForm.() -> CardForm) {
        _model.update { model ->
            if (model is Model.Editing && model.submission !is Submission.InProgress) {
                val form = model.form.update()
                model.copy(form = form, hasChanges = hasChanges(form))
            } else {
                model
            }
        }
        scheduleSync()
    }

    /** Debounces edits of a library card into one silent wallet update. */
    private fun scheduleSync(delayMillis: Long = SYNC_DEBOUNCE) {
        val editing = _model.value as? Model.Editing ?: return
        if (!autoSyncs(editing.form)) return
        syncJob?.cancel()
        if (!editing.hasChanges) {
            _model.value = editing.copy(sync = if (editing.sync == SyncStatus.NOT_IN_WALLET) SyncStatus.NOT_IN_WALLET else SyncStatus.SYNCED, syncError = null)
            return
        }
        if (!editing.form.isValid) {
            _model.value = editing.copy(sync = SyncStatus.PENDING)
            return
        }
        _model.value = editing.copy(sync = SyncStatus.PENDING, syncError = null)
        syncJob = scope.launch {
            delay(delayMillis)
            bannerJob?.join() // sync the final crop
            sync()
        }
    }

    fun syncNow() = scheduleSync(delayMillis = 0)

    private suspend fun sync() {
        val editing = _model.value as? Model.Editing ?: return
        val existing = editing.form.cardId?.let { library.get(it) } ?: return
        _model.update { (it as? Model.Editing)?.copy(sync = SyncStatus.SYNCING) ?: it }
        dependencies.walletService.updateCard(editing.form.toDraft(existing.walletId)).fold(
            ifLeft = { error ->
                val status = if (error.failure == WalletFailure.NOT_IN_WALLET) SyncStatus.NOT_IN_WALLET else SyncStatus.FAILED
                _model.update { (it as? Model.Editing)?.copy(sync = status, syncError = error.detail) ?: it }
            },
            ifRight = {
                val saved = withContext(NonCancellable) { saveToLibrary(editing.form, existing, existing.walletId) }
                issuedForm = saved
                _model.update { model ->
                    if (model !is Model.Editing) return@update model
                    // Keep edits made while syncing; adopt the library copies of the images that were synced.
                    val form = model.form.copy(
                        photo = if (model.form.photo == editing.form.photo) saved.photo else model.form.photo,
                        banner = if (model.form.banner == editing.form.banner) saved.banner else model.form.banner,
                    )
                    val changed = hasChanges(form)
                    model.copy(form = form, hasChanges = changed, sync = if (changed) SyncStatus.PENDING else SyncStatus.SYNCED, syncError = null)
                }
                if ((_model.value as? Model.Editing)?.hasChanges == true) scheduleSync()
            },
        )
    }

    /** Leaves the screen; unsynced edits of a library card are still pushed to the wallet in the background. */
    fun close() {
        val editing = _model.value as? Model.Editing
        if (editing != null && autoSyncs(editing.form) && editing.hasChanges && editing.form.isValid) {
            isFlushingOnClose = true
            syncJob?.cancel()
            CoroutineScope(Dispatchers.Main + SupervisorJob()).launch {
                withContext(NonCancellable) {
                    bannerJob?.join()
                    sync()
                    // The working copies were moved into the library by the sync, or are no longer needed.
                    val form = (_model.value as? Model.Editing)?.form ?: editing.form
                    deleteUnlessInLibrary(form.photo)
                    form.banner?.let { deleteUnlessInLibrary(it) }
                }
                scope.cancel()
            }
        }
        onFinished()
    }

    /** Decoded photo for colour sampling, kept while the user drags across it. */
    private var samplePixels: Pair<StoredImage, ImagePixels>? = null
    private var previewJob: Job? = null

    fun previewPhotoColor(x: Float, y: Float) {
        val editing = _model.value as? Model.Editing ?: return
        val photo = editing.form.photo
        samplePixels?.takeIf { it.first == photo }?.let { (_, pixels) ->
            setColorPreview(ColorPalette.sample(pixels, x, y))
            return
        }
        // First sample decodes the photo; later points of a drag reuse it.
        if (previewJob?.isActive == true) return
        previewJob = scope.launch {
            val pixels = dependencies.imageProcessor.pixels(photo, maxSize = COLOR_SAMPLE_SIZE).getOrNull() ?: return@launch
            samplePixels = photo to pixels
            setColorPreview(ColorPalette.sample(pixels, x, y))
        }
    }

    fun setColorPreview(color: CardColor?) {
        _model.update { (it as? Model.Editing)?.copy(colorPreview = color) ?: it }
    }

    fun confirmColorPreview() {
        val color = (_model.value as? Model.Editing)?.colorPreview ?: return
        setColorPreview(null)
        updateForm { withCustomColor(color) }
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
            val banner = dependencies.bannerCropper.crop(editing.form.photo, clamped).getOrNull() ?: return@launch
            val current = _model.value as? Model.Editing
            if (current == null || current.form.bannerRegion != clamped) {
                imageStore.delete(banner) // superseded by a newer crop
                return@launch
            }
            val form = current.form.copy(banner = banner, isBannerEnabled = true)
            _model.value = current.copy(form = form, hasChanges = hasChanges(form))
            current.form.banner?.let { deleteUnlessInLibrary(it) }
            scheduleSync()
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
            val existing = editing.form.cardId?.let { library.get(it) }
            val walletId = existing?.walletId ?: newId()
            val submission = dependencies.walletService.addCard(editing.form.toDraft(walletId)).fold(
                // Dismissing the system sheet just returns to the form.
                ifLeft = { if (it.failure == WalletFailure.CANCELLED) Submission.Idle else Submission.Failed(it.failure, it.detail) },
                ifRight = { added ->
                    val saved = withContext(NonCancellable) { saveToLibrary(editing.form, existing, walletId) }
                    issuedForm = saved
                    _model.update { model ->
                        (model as? Model.Editing)?.copy(form = saved, hasChanges = false, sync = SyncStatus.SYNCED.takeIf { autoSyncs(saved) }) ?: model
                    }
                    Submission.Added(added.walletUrl, added.replacedExisting)
                },
            )
            _model.update { (it as? Model.Editing)?.copy(submission = submission) ?: it }
        }
    }

    /** Persists the card with its images copied into the library; returns the form pointing at those copies. */
    private suspend fun saveToLibrary(form: CardForm, existing: StoredCard?, walletId: String): CardForm {
        val photo = library.keepImage(form.photo)
        val banner = form.banner?.let { library.keepImage(it) }
        val now = Clock.System.now().toEpochMilliseconds()
        val saved = form.copy(photo = photo, banner = banner, cardId = form.cardId ?: newId())
        library.save(
            StoredCard(
                id = saved.cardId!!,
                walletId = walletId,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now,
                form = SavedCardForm.of(saved),
            ),
        )
        // The cache working copies are no longer needed, nor is a library banner replaced by a new crop.
        if (photo != form.photo) imageStore.delete(form.photo)
        if (banner != form.banner) form.banner?.let { imageStore.delete(it) }
        existing?.form?.banner?.takeIf { it != banner?.path }?.let { imageStore.delete(StoredImage(it)) }
        return saved
    }

    fun delete() {
        val cardId = (_model.value as? Model.Editing)?.form?.cardId ?: return
        syncJob?.cancel()
        scope.launch {
            withContext(NonCancellable) {
                library.deleteCard(cardId, { dependencies.walletService.removeCard(it) }, imageStore::delete)
            }
            onFinished()
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
        suggestedColorCount = colors.size,
        banner = banner,
        isBannerEnabled = banner != null,
        bannerRegion = bannerRegion,
        photoAspectRatio = photoAspectRatio,
        walletPlatform = dependencies.walletService.platform,
        cardId = null,
    )

    private fun CardForm.toDraft(walletId: String) = CardDraft(
        walletId = walletId,
        name = name.trim(),
        code = selectedCode.copy(value = selectedCode.value.trim()),
        backgroundColor = selectedColor,
        banner = banner.takeIf { isBannerEnabled },
    )

    /** Whether [form] differs from the issued card in anything that ends up in the pass. */
    private fun hasChanges(form: CardForm): Boolean {
        val issued = issuedForm ?: return true
        return form.name.trim() != issued.name.trim() ||
            form.selectedCode != issued.selectedCode ||
            form.selectedColor != issued.selectedColor ||
            form.isBannerEnabled != issued.isBannerEnabled ||
            (form.isBannerEnabled && form.bannerRegion != issued.bannerRegion)
    }

    private fun refreshChanges() {
        _model.update { model -> (model as? Model.Editing)?.let { it.copy(hasChanges = hasChanges(it.form)) } ?: model }
    }

    private suspend fun deleteUnlessInLibrary(image: StoredImage) {
        if (!library.isLibraryImage(image)) imageStore.delete(image)
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun newId() = Uuid.random().toString()

    private companion object {
        const val BANNER_RECROP_DEBOUNCE = 250L
        const val COLOR_SAMPLE_SIZE = 512
        const val SYNC_DEBOUNCE = 1_200L
    }

    override fun onDestroy() {
        // A sync still running on close owns the images and the model (see [close]).
        if (isFlushingOnClose) return
        scope.cancel()
        // Working copies in the cache go; images that belong to a library card stay.
        (_model.value as? Model.Editing)?.form?.let { form ->
            CoroutineScope(Dispatchers.Main).launch {
                withContext(NonCancellable) {
                    deleteUnlessInLibrary(form.photo)
                    form.banner?.let { deleteUnlessInLibrary(it) }
                }
            }
        }
    }
}

/** Replaces the custom colour (the palette entry after the suggestions) with [color] and selects it. */
private fun CardForm.withCustomColor(color: CardColor): CardForm =
    copy(colors = colors.take(suggestedColorCount) + color, selectedColorIndex = suggestedColorCount)
