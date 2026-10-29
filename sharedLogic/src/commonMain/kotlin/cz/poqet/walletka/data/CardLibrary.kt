package cz.poqet.walletka.data

import cz.poqet.walletka.component.review.SavedCardForm
import cz.poqet.walletka.domain.StoredImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock

/** A card Walletka has added to the wallet, with everything needed to edit and re-issue it. */
@Serializable
internal data class StoredCard(
    val id: String,
    /** Identity of the pass in the wallet: the Apple serial number, reused so edits replace the pass. */
    val walletId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val form: SavedCardForm,
    /** `false` for a card restored from a backup ([LibraryBackup]) until it has been added to this wallet. */
    val addedToWallet: Boolean = true,
)

/** Paths of every image [StoredCard] uses: photo (absent for cards entered by hand), banner and logo. */
internal val StoredCard.imagePaths: List<String>
    get() = listOfNotNull(form.photo, form.banner, form.logo?.image)

/** [StoredCard] with every image path passed through [transform]. */
internal fun StoredCard.withImagePaths(transform: (String) -> String) = copy(
    form = form.copy(
        photo = form.photo?.let(transform),
        banner = form.banner?.let(transform),
        logo = form.logo?.let { it.copy(image = it.image?.let(transform)) },
    ),
)

/** Hears about local changes of the library ([CardLibrary.changeListener]). */
internal fun interface LibraryChangeListener {
    /** Cards with [cardIds] were saved or deleted; [orderChanged] when the manual order changed. */
    fun onLocalChange(cardIds: Set<String>, orderChanged: Boolean)
}

/**
 * The cards created in Walletka, persisted as JSON in the app's data directory. Their photos and banners are copied
 * next to it ([keepImage]; cards entered by hand have neither) so they outlive the cache the review flow works in.
 */
internal class CardLibrary(
    directory: String,
    private val ioContext: CoroutineContext = Dispatchers.Default,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val root = Path(directory, "library")
    private val imagesDirectory = Path(root, "images")
    private val file = Path(root, "cards.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredCard.serializer())
    private val syncFile = Path(root, "sync.json")
    private val mutex = Mutex()
    private var loaded = false

    private val _cards = MutableStateFlow<List<StoredCard>>(emptyList())
    private val _sync = MutableStateFlow(SyncMetadata())

    /** In the user's order ([move]); new cards go on top. Call [load] once to fill it. */
    val cards: StateFlow<List<StoredCard>> = _cards.asStateFlow()

    suspend fun load() {
        locked { }
    }

    suspend fun get(id: String): StoredCard? = locked { _cards.value.firstOrNull { it.id == id } }

    /** Adds [card] on top, or replaces the card with its id in place. */
    suspend fun save(card: StoredCard) = locked {
        val cards = _cards.value
        val index = cards.indexOfFirst { it.id == card.id }
        write(if (index < 0) listOf(card) + cards else cards.toMutableList().apply { set(index, card) })
        changeListener?.onLocalChange(setOf(card.id), orderChanged = index < 0)
    }

    /** Moves the card with [id] to [toIndex] of [cards] (the manual order of the list). */
    suspend fun move(id: String, toIndex: Int) = locked {
        val cards = _cards.value.toMutableList()
        val index = cards.indexOfFirst { it.id == id }
        if (index < 0) return@locked
        val card = cards.removeAt(index)
        cards.add(toIndex.coerceIn(0, cards.size), card)
        if (cards == _cards.value) return@locked
        write(cards, _sync.value.copy(orderUpdatedAt = clock()))
        changeListener?.onLocalChange(emptySet(), orderChanged = true)
    }

    /**
     * Merges changes from another copy of the library (iCloud, a backup) by the [LibraryMerge] rules, in one write.
     * Images of the cards must already be in the library ([imageFile]); images no card uses any more are deleted.
     * [fromThisDevice] (a restored backup) revives deleted cards and reports the accepted cards as local changes.
     */
    internal suspend fun applyRemote(changes: List<RemoteChange>, fromThisDevice: Boolean = false): LibraryMerge.Result {
        val result = locked {
            val result = LibraryMerge.apply(LibraryState(_cards.value, _sync.value), changes, revive = fromThisDevice, now = clock())
            write(result.state.cards, result.state.sync)
            deleteUnusedImages(result.dropped)
            result
        }
        val changed = result.newerHere + if (fromThisDevice) result.accepted else emptySet()
        val orderChanged = result.isOrderNewerHere || (fromThisDevice && result.accepted.isNotEmpty())
        if (changed.isNotEmpty() || orderChanged) changeListener?.onLocalChange(changed, orderChanged)
        return result
    }

    /** Tombstones and order time, for [LibraryMerge]. */
    val syncMetadata: StateFlow<SyncMetadata> = _sync.asStateFlow()

    /** Told about every local change, e.g. to upload it to iCloud. */
    var changeListener: LibraryChangeListener? = null

    /** Where the library keeps an image called [name]; for restoring backups. */
    internal fun imageFile(name: String): Path = Path(imagesDirectory, name).also { fileSystem.createDirectories(imagesDirectory) }

    suspend fun delete(id: String): StoredCard? = locked {
        val card = _cards.value.firstOrNull { it.id == id } ?: return@locked null
        write(_cards.value - card, _sync.value.copy(tombstones = _sync.value.tombstones + (id to clock())))
        changeListener?.onLocalChange(setOf(id), orderChanged = false)
        card
    }

    /** Copies [image] into the library unless it already lives there. */
    suspend fun keepImage(image: StoredImage): StoredImage = withContext(ioContext) {
        if (isLibraryImage(image)) return@withContext image
        fileSystem.createDirectories(imagesDirectory)
        val target = Path(imagesDirectory, Path(image.path).name)
        fileSystem.source(Path(image.path)).buffered().use { source ->
            fileSystem.sink(target).buffered().use { sink -> source.transferTo(sink) }
        }
        StoredImage(target.toString())
    }

    /**
     * Deletes the card with its images, and its pass where [removePass] can (Apple Wallet). Returns the removed card.
     */
    suspend fun deleteCard(
        id: String,
        removePass: suspend (walletId: String) -> Unit,
        deleteImage: suspend (StoredImage) -> Unit,
    ): StoredCard? {
        val card = delete(id) ?: return null
        removePass(card.walletId)
        card.imagePaths.forEach { deleteImage(StoredImage(it)) }
        return card
    }

    fun isLibraryImage(image: StoredImage): Boolean = image.path.startsWith(imagesDirectory.toString())

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
        if (!loaded) {
            _cards.value = withContext(ioContext) { read() }
            _sync.value = withContext(ioContext) { readSync() }
            loaded = true
        }
        block()
    }

    private fun read(): List<StoredCard> {
        if (!fileSystem.exists(file)) return emptyList()
        val text = fileSystem.source(file).buffered().use { it.readString() }
        // A corrupt library must not lock the user out of the app.
        val cards = runCatching { json.decodeFromString(serializer, text) }.getOrDefault(emptyList())
        return cards.map { it.withImagePaths(::resolve) }
    }

    /**
     * Image paths are stored relative to [imagesDirectory]: the app's container path can change between installs
     * and updates, so absolute paths would break. Paths that are already absolute are re-anchored by file name.
     */
    private fun relativize(path: String): String =
        if (path.startsWith(imagesDirectory.toString())) Path(path).name else path

    private fun resolve(path: String): String {
        if (path.startsWith("/") && fileSystem.exists(Path(path))) return path
        return Path(imagesDirectory, Path(path).name).toString()
    }

    private suspend fun write(cards: List<StoredCard>, sync: SyncMetadata = _sync.value) {
        withContext(ioContext) {
            fileSystem.createDirectories(root)
            if (sync != _sync.value) writeAtomically(syncFile, json.encodeToString(SyncMetadata.serializer(), sync))
            val stored = cards.map { it.withImagePaths(::relativize) }
            writeAtomically(file, json.encodeToString(serializer, stored))
        }
        _sync.value = sync
        _cards.value = cards
    }

    private fun writeAtomically(target: Path, text: String) {
        val temporary = Path(root, "${target.name}.tmp")
        fileSystem.sink(temporary).buffered().use { it.writeString(text) }
        fileSystem.atomicMove(temporary, target)
    }

    private fun readSync(): SyncMetadata {
        if (!fileSystem.exists(syncFile)) return SyncMetadata()
        val text = fileSystem.source(syncFile).buffered().use { it.readString() }
        return runCatching { json.decodeFromString(SyncMetadata.serializer(), text) }.getOrDefault(SyncMetadata())
    }

    /** Deletes the library images of [cards] that no current card uses. */
    private suspend fun deleteUnusedImages(cards: List<StoredCard>) = withContext(ioContext) {
        val used = _cards.value.flatMapTo(HashSet()) { it.imagePaths }
        cards.flatMap { it.imagePaths }
            .filter { it !in used && isLibraryImage(StoredImage(it)) }
            .forEach { fileSystem.delete(Path(it), mustExist = false) }
    }
}
