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

/** A card Walletka has added to the wallet, with everything needed to edit and re-issue it. */
@Serializable
internal data class StoredCard(
    val id: String,
    /** Identity of the pass in the wallet: the Apple serial number, reused so edits replace the pass. */
    val walletId: String,
    val createdAt: Long,
    val updatedAt: Long,
    val form: SavedCardForm,
)

/**
 * The cards created in Walletka, persisted as JSON in the app's data directory. Their photos and banners are copied
 * next to it ([keepImage]) so they outlive the cache the review flow works in.
 */
internal class CardLibrary(
    directory: String,
    private val ioContext: CoroutineContext = Dispatchers.Default,
    private val fileSystem: FileSystem = SystemFileSystem,
) {
    private val root = Path(directory, "library")
    private val imagesDirectory = Path(root, "images")
    private val file = Path(root, "cards.json")
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(StoredCard.serializer())
    private val mutex = Mutex()
    private var loaded = false

    private val _cards = MutableStateFlow<List<StoredCard>>(emptyList())

    /** Newest first. Call [load] once to fill it. */
    val cards: StateFlow<List<StoredCard>> = _cards.asStateFlow()

    suspend fun load() {
        locked { }
    }

    suspend fun get(id: String): StoredCard? = locked { _cards.value.firstOrNull { it.id == id } }

    suspend fun save(card: StoredCard) = locked {
        write(listOf(card) + _cards.value.filterNot { it.id == card.id })
    }

    suspend fun delete(id: String): StoredCard? = locked {
        val card = _cards.value.firstOrNull { it.id == id } ?: return@locked null
        write(_cards.value - card)
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
        deleteImage(StoredImage(card.form.photo))
        card.form.banner?.let { deleteImage(StoredImage(it)) }
        card.form.logo?.image?.let { deleteImage(StoredImage(it)) }
        return card
    }

    fun isLibraryImage(image: StoredImage): Boolean = image.path.startsWith(imagesDirectory.toString())

    private suspend fun <T> locked(block: suspend () -> T): T = mutex.withLock {
        if (!loaded) {
            _cards.value = withContext(ioContext) { read() }
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

    private fun StoredCard.withImagePaths(transform: (String) -> String) = copy(
        form = form.copy(
            photo = transform(form.photo),
            banner = form.banner?.let(transform),
            logo = form.logo?.let { it.copy(image = it.image?.let(transform)) },
        ),
    )

    private suspend fun write(cards: List<StoredCard>) {
        withContext(ioContext) {
            fileSystem.createDirectories(root)
            val temporary = Path(root, "cards.json.tmp")
            val stored = cards.map { it.withImagePaths(::relativize) }
            fileSystem.sink(temporary).buffered().use { it.writeString(json.encodeToString(serializer, stored)) }
            fileSystem.atomicMove(temporary, file)
        }
        _cards.value = cards
    }
}
