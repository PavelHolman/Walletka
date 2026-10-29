package cz.poqet.walletka.data

import cz.poqet.walletka.component.tempDirectory
import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.coroutines.test.runTest
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LibraryBackupTest {

    private fun library(directory: Path = tempDirectory("library"), clock: () -> Long = { 1_000 }) =
        CardLibrary(directory.toString(), EmptyCoroutineContext, clock = clock)

    /** Saves a card whose photo is a real file in the library. */
    private suspend fun CardLibrary.add(id: String, updatedAt: Long = 1, name: String = id) {
        val photo = imageFile("$id.jpg")
        SystemFileSystem.sink(photo).buffered().use { it.writeString("photo of $id") }
        save(storedCard(id, updatedAt = updatedAt, name = name, photo = photo.toString()))
    }

    private fun backup(library: CardLibrary, platform: WalletPlatform = WalletPlatform.GOOGLE_WALLET) =
        LibraryBackup(library, platform, EmptyCoroutineContext, clock = { 1_761_696_000_000 })

    @Test
    fun fileNameCarriesTheDate() {
        assertEquals("Walletka-backup-2025-10-29.walletka", backup(library()).fileName())
    }

    @Test
    fun exportThenImportRestoresCardsAndImages() = runTest {
        val source = library()
        source.add("a")
        source.add("b")
        val file = Path(tempDirectory("export"), "backup.walletka")
        assertEquals(2, backup(source).export(file))

        val target = library()
        val summary = backup(target).import(file)

        assertEquals(ImportSummary(added = 2, updated = 0, skipped = 0), summary)
        val cards = target.cards.value
        assertEquals(listOf("b", "a"), cards.map { it.id }) // the exported order
        assertTrue(cards.none { it.addedToWallet })
        assertTrue(cards.all { target.isLibraryImage(cz.poqet.walletka.domain.StoredImage(it.form.photo)) })
        assertEquals("photo of a", SystemFileSystem.source(Path(cards[1].form.photo)).buffered().use { it.readString() })
        assertEquals(WalletPlatform.GOOGLE_WALLET, cards[0].form.walletPlatform) // made on iOS, re-targeted
    }

    @Test
    fun importSkipsCardsThatAreNotNewerAndReplacesOlderOnes() = runTest {
        val source = library()
        source.add("same", updatedAt = 5)
        source.add("newer", updatedAt = 9, name = "From backup")
        val file = Path(tempDirectory("export"), "backup.walletka")
        backup(source).export(file)

        val target = library()
        target.add("same", updatedAt = 5)
        target.add("newer", updatedAt = 2, name = "Old")

        assertEquals(ImportSummary(added = 0, updated = 1, skipped = 1), backup(target).import(file))
        assertEquals("From backup", target.cards.value.first { it.id == "newer" }.form.name)
        // Replacing doesn't make an existing card look like it left the wallet.
        assertTrue(target.cards.value.all { it.addedToWallet })
    }

    @Test
    fun importingSomethingElseFails() = runTest {
        val file = Path(tempDirectory("export"), "notes.walletka")
        SystemFileSystem.sink(file).buffered().use { it.writeString("not a zip") }
        assertFailsWith<ZipException> { backup(library()).import(file) }
    }

    @Test
    fun zipRoundTrip() {
        val buffer = kotlinx.io.Buffer()
        Zip.Writer(buffer).apply {
            entry("a.txt", "hello".encodeToByteArray())
            entry("images/b.png", ByteArray(300) { it.toByte() })
            finish()
        }
        val entries = mutableListOf<Pair<String, ByteArray>>()
        Zip.read(buffer) { name, data -> entries += name to data }
        assertEquals(listOf("a.txt", "images/b.png"), entries.map { it.first })
        assertEquals("hello", entries[0].second.decodeToString())
        assertEquals(300, entries[1].second.size)
    }

    @Test
    fun libraryKeepsTombstonesAndOrderTimeAcrossLaunches() = runTest {
        val directory = tempDirectory("library")
        var now = 10L
        val first = library(directory) { now }
        first.add("a")
        first.add("b")
        now = 20
        first.move("a", 0)
        now = 30
        first.delete("b")

        val reopened = library(directory)
        reopened.load()
        assertEquals(listOf("a"), reopened.cards.value.map { it.id })
        assertEquals(SyncMetadata(tombstones = mapOf("b" to 30L), orderUpdatedAt = 20), reopened.syncMetadata.value)
        assertTrue(reopened.cards.value.single().addedToWallet)
    }

    @Test
    fun localChangesAreReported() = runTest {
        val library = library()
        val changes = mutableListOf<Pair<Set<String>, Boolean>>()
        library.changeListener = LibraryChangeListener { ids, order -> changes += ids to order }
        library.add("a")
        library.add("b")
        library.move("a", 0)
        library.add("a", updatedAt = 3)
        library.delete("b")
        assertEquals(
            listOf(setOf("a") to true, setOf("b") to true, emptySet<String>() to true, setOf("a") to false, setOf("b") to false),
            changes,
        )
    }
}
