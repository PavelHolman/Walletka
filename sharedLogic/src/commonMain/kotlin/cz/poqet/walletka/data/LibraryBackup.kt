package cz.poqet.walletka.data

import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.FileSystem
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext
import kotlin.time.Clock
import kotlin.time.Instant

/** The `cards.json` entry of a backup; the images are `images/<name>` entries. */
@Serializable
private class BackupManifest(val version: Int, val exportedAt: Long, val cards: List<StoredCard>)

/** What importing a backup did. */
internal data class ImportSummary(val added: Int, val updated: Int, val skipped: Int)

/**
 * Exports the whole library into one `.walletka` file (a ZIP archive with the cards as JSON and their images) and
 * merges such a file back: new cards are added (not in this device's wallet), cards that exist are replaced when the
 * backup's version is newer, and kept otherwise.
 */
internal class LibraryBackup(
    private val library: CardLibrary,
    private val platform: WalletPlatform,
    private val ioContext: CoroutineContext = Dispatchers.Default,
    private val fileSystem: FileSystem = SystemFileSystem,
    private val clock: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** `Walletka-backup-YYYY-MM-DD.walletka` for today (UTC). */
    fun fileName(): String = "Walletka-backup-${Instant.fromEpochMilliseconds(clock()).toString().take(10)}.$EXTENSION"

    /** Writes the backup to [target], replacing it; returns how many cards it holds. */
    suspend fun export(target: Path): Int {
        library.load()
        val cards = library.cards.value
        withContext(ioContext) {
            target.parent?.let { fileSystem.createDirectories(it) }
            fileSystem.sink(target).buffered().use { sink ->
                val zip = Zip.Writer(sink)
                val manifest = BackupManifest(VERSION, clock(), cards.map(CardCodec::portable))
                zip.entry(MANIFEST, json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray())
                val written = mutableSetOf<String>()
                for (card in cards) {
                    for (path in listOfNotNull(card.form.photo, card.form.banner)) {
                        val file = Path(path)
                        if (!written.add(file.name) || !fileSystem.exists(file)) continue
                        zip.entry("$IMAGES${file.name}", fileSystem.source(file).buffered().use { it.readByteArray() })
                    }
                }
                zip.finish()
            }
        }
        return cards.size
    }

    /** Merges the backup at [source] (only read) into the library. Throws [ZipException] for an invalid file. */
    suspend fun import(source: Path): ImportSummary {
        library.load()
        val before = library.cards.value.mapTo(HashSet()) { it.id }
        var total = 0
        val changes = withContext(ioContext) {
            var accepted: Map<String, StoredCard>? = null
            fileSystem.source(source).buffered().use { input ->
                Zip.read(input) { name, data ->
                    if (name == MANIFEST) {
                        val manifest = runCatching { json.decodeFromString(BackupManifest.serializer(), data.decodeToString()) }
                            .getOrElse { throw ZipException("Not a Walletka backup") }
                        if (manifest.version > VERSION) throw ZipException("Made by a newer Walletka")
                        total = manifest.cards.size
                        val cards = manifest.cards.map { card ->
                            CardCodec.local(card, platform) { library.imageFile(it).toString() }
                        }
                        // Only the cards the merge takes need their images.
                        val preview = LibraryMerge.apply(
                            LibraryState(library.cards.value, library.syncMetadata.value),
                            cards.map(RemoteChange::Card),
                            revive = true,
                        )
                        accepted = cards.filter { it.id in preview.accepted }.associateBy { it.id }
                    } else if (name.startsWith(IMAGES)) {
                        val imageName = Path(name).name
                        val needed = accepted ?: throw ZipException("Not a Walletka backup")
                        if (needed.values.any { imageName in CardCodec.imageNames(it) }) {
                            fileSystem.sink(library.imageFile(imageName)).buffered().use { it.write(data) }
                        }
                    }
                }
            }
            (accepted ?: throw ZipException("Not a Walletka backup")).values.map(RemoteChange::Card)
        }
        val result = library.applyRemote(changes, fromThisDevice = true)
        val added = result.accepted.count { it !in before }
        return ImportSummary(added = added, updated = result.accepted.size - added, skipped = total - result.accepted.size)
    }

    companion object {
        const val EXTENSION = "walletka"
        private const val VERSION = 1
        private const val MANIFEST = "cards.json"
        private const val IMAGES = "images/"
    }
}
