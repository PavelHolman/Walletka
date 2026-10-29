package cz.poqet.walletka.component.home

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import com.arkivanov.essenty.lifecycle.doOnResume
import cz.poqet.walletka.component.home.HomeComponent.CardSummary
import cz.poqet.walletka.component.home.HomeComponent.Model
import cz.poqet.walletka.data.CardLibrary
import cz.poqet.walletka.data.LibraryBackup
import cz.poqet.walletka.data.StoredCard
import cz.poqet.walletka.platform.CloudSyncStatus
import cz.poqet.walletka.platform.ImageStore
import cz.poqet.walletka.platform.LibrarySync
import cz.poqet.walletka.platform.WalletService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.coroutines.CoroutineContext

internal class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val walletService: WalletService,
    private val library: CardLibrary,
    private val imageStore: ImageStore,
    private val onImagePicked: (source: String) -> Unit,
    private val onCardClicked: (cardId: String) -> Unit,
    private val onEnterManually: () -> Unit,
    private val backup: LibraryBackup? = null,
    /** Where exported backups wait for the system UI to save or share them. */
    private val exportDirectory: String? = null,
    private val librarySync: LibrarySync? = null,
    private val ioContext: CoroutineContext = Dispatchers.Default,
) : HomeComponent, ComponentContext by componentContext {

    private val scope = coroutineScope(Dispatchers.Main.immediate)

    /** Everything the model is built from. */
    private data class State(
        val cards: List<StoredCard>,
        val isWalletAvailable: Boolean? = null,
        val inWallet: Map<String, Boolean?> = emptyMap(),
        val query: String = "",
        val backup: BackupState = BackupState.Idle,
        val cloudSync: CloudSyncStatus? = null,
        val cloudSyncError: String? = null,
    )

    private val state = MutableStateFlow(State(library.cards.value, cloudSync = librarySync?.status?.value))
    private val _model = MutableStateFlow(state.value.toModel())
    override val model: StateFlow<Model> = _model.asStateFlow()

    private var walletStatusJob: Job? = null

    init {
        scope.launch {
            val available = walletService.isAvailable()
            update { it.copy(isWalletAvailable = available) }
        }
        scope.launch {
            library.load()
            library.cards.collect { cards ->
                update { it.copy(cards = cards) }
                refreshWalletStatus()
            }
        }
        librarySync?.let { sync ->
            scope.launch { sync.status.collect { status -> update { it.copy(cloudSync = status) } } }
            scope.launch { sync.lastError.collect { error -> update { it.copy(cloudSyncError = error) } } }
        }
        // A pass may have been removed in the Wallet app meanwhile.
        lifecycle.doOnResume { refreshWalletStatus() }
    }

    override fun onImagePicked(source: String) {
        onImagePicked.invoke(source)
    }

    override fun onEnterManuallyClicked() {
        onEnterManually.invoke()
    }

    override fun onCardClicked(cardId: String) {
        onCardClicked.invoke(cardId)
    }

    override fun onCardDeleteClicked(cardId: String) {
        scope.launch {
            withContext(NonCancellable) {
                library.deleteCard(cardId, { walletService.removeCard(it) }, imageStore::delete)
            }
        }
    }

    override fun onSearchQueryChanged(query: String) {
        update { it.copy(query = query) }
    }

    override fun onCardMoved(fromIndex: Int, toIndex: Int) {
        if (!_model.value.canReorder) return
        val card = state.value.cards.getOrNull(fromIndex) ?: return
        // Shown right away; the library confirms it once written.
        update { it.copy(cards = it.cards.toMutableList().apply { add(toIndex.coerceIn(0, size - 1), removeAt(fromIndex)) }) }
        scope.launch { withContext(NonCancellable) { library.move(card.id, toIndex) } }
    }

    override fun onExportClicked() {
        val backup = backup ?: return
        val directory = exportDirectory ?: return
        if (state.value.backup == BackupState.InProgress) return
        update { it.copy(backup = BackupState.InProgress) }
        scope.launch {
            val fileName = backup.fileName()
            val target = Path(directory, fileName)
            val result = runCatching {
                deleteExports()
                backup.export(target)
            }
            result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
            update {
                it.copy(
                    backup = result.fold(
                        onSuccess = { count -> BackupState.ExportReady(target.toString(), fileName, count) },
                        onFailure = { error -> BackupState.Failed(BackupOperation.EXPORT, error.message) },
                    ),
                )
            }
        }
    }

    override fun onExportFinished() {
        update { it.copy(backup = BackupState.Idle) }
        scope.launch { runCatching { deleteExports() } }
    }

    override fun onImportFileSelected(path: String) {
        val backup = backup ?: return
        if (state.value.backup == BackupState.InProgress) return
        update { it.copy(backup = BackupState.InProgress) }
        scope.launch {
            val result = runCatching { withContext(NonCancellable) { backup.import(Path(path)) } }
            update {
                it.copy(
                    backup = result.fold(
                        onSuccess = { summary -> BackupState.Imported(summary.added, summary.updated, summary.skipped) },
                        onFailure = { error -> BackupState.Failed(BackupOperation.IMPORT, error.message) },
                    ),
                )
            }
        }
    }

    override fun onBackupMessageDismissed() {
        update { if (it.backup is BackupState.InProgress) it else it.copy(backup = BackupState.Idle) }
    }

    override fun onCloudSyncToggled(enabled: Boolean) {
        librarySync?.setEnabled(enabled)
    }

    override fun onSyncNowClicked() {
        librarySync?.syncNow()
    }

    private fun update(transform: (State) -> State) {
        state.update(transform)
        _model.value = state.value.toModel()
    }

    private fun refreshWalletStatus() {
        walletStatusJob?.cancel()
        walletStatusJob = scope.launch {
            val inWallet = state.value.cards.associate { card ->
                // Restored cards that were never added here are known not to be in this wallet.
                card.id to (walletService.isInWallet(card.walletId) ?: false.takeIf { !card.addedToWallet })
            }
            update { it.copy(inWallet = inWallet) }
        }
    }

    private suspend fun deleteExports() {
        val directory = exportDirectory?.let(::Path) ?: return
        withContext(ioContext) {
            if (SystemFileSystem.exists(directory)) SystemFileSystem.list(directory).forEach { SystemFileSystem.delete(it, mustExist = false) }
        }
    }

    private fun State.toModel(): Model {
        val summaries = cards.map { it.toSummary(inWallet[it.id]) }
        val isSearchAvailable = cards.size > SEARCH_THRESHOLD
        val query = query.trim().takeIf { isSearchAvailable }.orEmpty()
        return Model(
            walletPlatform = walletService.platform,
            isWalletAvailable = isWalletAvailable,
            cards = summaries,
            visibleCards = if (query.isEmpty()) summaries else summaries.filter { it.matches(query) },
            searchQuery = this.query,
            isSearchAvailable = isSearchAvailable,
            canReorder = query.isEmpty(),
            backup = backup,
            cloudSync = cloudSync,
            cloudSyncError = cloudSyncError,
        )
    }

    private fun CardSummary.matches(query: String) =
        name.contains(query, ignoreCase = true) || codeValue.contains(query, ignoreCase = true)

    private fun StoredCard.toSummary(isInWallet: Boolean?): CardSummary {
        val form = form.toForm()
        return CardSummary(
            id = id,
            name = form.name,
            color = form.selectedColor,
            codeFormat = form.selectedCode.format,
            codeValue = form.selectedCode.value,
            banner = form.banner.takeIf { form.isBannerEnabled },
            isInWallet = isInWallet,
        )
    }

    private companion object {
        /** Search is offered for more cards than this. */
        const val SEARCH_THRESHOLD = 6
    }
}
