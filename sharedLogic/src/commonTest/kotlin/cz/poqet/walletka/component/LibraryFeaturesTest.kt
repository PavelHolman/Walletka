package cz.poqet.walletka.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import cz.poqet.walletka.component.home.BackupState
import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.component.review.CardReviewComponent.SyncStatus
import cz.poqet.walletka.component.review.DuplicateCard
import cz.poqet.walletka.component.root.DefaultRootComponent
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.data.CardLibrary
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.platform.PlatformServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Duplicate check, reordering, search and backups, driven through the components. */
@OptIn(ExperimentalCoroutinesApi::class)
class LibraryFeaturesTest {

    private val lifecycle = LifecycleRegistry()
    private val codes = mutableListOf(ScannedCode(CodeFormat.QR, "CODE-1"))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun services(wallet: FakeWalletService = FakeWalletService(), dataDirectory: Path = tempDirectory("data")) = PlatformServices(
        imageStore = FakeImageStore(),
        barcodeDetector = { arrow.core.Either.Right(codes.toList()) },
        textRecognizer = { arrow.core.Either.Right(emptyList()) },
        imageProcessor = FakeImageProcessor(),
        walletService = wallet,
        dataDirectory = dataDirectory.toString(),
        ioContext = EmptyCoroutineContext,
    )

    private fun root(services: PlatformServices) =
        DefaultRootComponent(DefaultComponentContext(lifecycle), services).also { lifecycle.resume() }

    private fun RootComponent.review() = assertIs<RootComponent.Child.Review>(children.value.last()).component

    private fun RootComponent.home() = assertIs<RootComponent.Child.Home>(children.value.first()).component

    /** Adds a card for a photo whose code is [code]. */
    private fun RootComponent.addCard(code: String, name: String = code) {
        codes[0] = ScannedCode(CodeFormat.QR, code)
        onImageReceived("$code.jpg")
        review().onNameChanged(name)
        review().onAddToWalletClicked()
        review().onCloseClicked()
    }

    @Test
    fun newCardWithAKnownCodeIsFlaggedAsDuplicate() {
        val root = root(services())
        root.addCard("CODE-1", name = "Coffee")

        root.onImageReceived("again.jpg")
        val editing = assertIs<Model.Editing>(root.review().model.value)
        val existing = root.home().model.value.cards.single()
        assertEquals(DuplicateCard(existing.id, "Coffee"), editing.duplicateOf)

        // Another code is no duplicate…
        root.review().onCodeValueChanged("OTHER")
        assertNull(assertIs<Model.Editing>(root.review().model.value).duplicateOf)
        // …surrounding spaces don't hide one.
        root.review().onCodeValueChanged(" CODE-1 ")
        assertEquals(existing.id, assertIs<Model.Editing>(root.review().model.value).duplicateOf?.cardId)
    }

    @Test
    fun typedCodeMatchingAKnownCardIsFlaggedAsDuplicate() {
        val root = root(services())
        root.addCard("CODE-1", name = "Coffee")

        root.home().onEnterManuallyClicked()
        // Nothing typed yet: no duplicate.
        assertNull(assertIs<Model.Editing>(root.review().model.value).duplicateOf)
        root.review().onCodeFormatSelected(CodeFormat.QR)
        root.review().onCodeValueChanged("CODE-1")
        assertEquals("Coffee", assertIs<Model.Editing>(root.review().model.value).duplicateOf?.name)
    }

    @Test
    fun openingTheDuplicateReplacesTheReviewWithItsPreview() {
        val root = root(services())
        root.addCard("CODE-1")
        root.onImageReceived("again.jpg")

        root.review().onOpenDuplicateClicked()

        assertEquals(2, root.children.value.size)
        val preview = assertIs<RootComponent.Child.Preview>(root.children.value.last()).component
        assertEquals(root.home().model.value.cards.single().id, preview.cardId)
    }

    @Test
    fun duplicateCanStillBeAdded() {
        val root = root(services())
        root.addCard("CODE-1")
        root.addCard("CODE-1", name = "Second")
        assertEquals(listOf("Second", "CODE-1"), root.home().model.value.cards.map { it.name })
    }

    @Test
    fun newCardsGoOnTopAndEditsKeepThePosition() {
        val root = root(services())
        root.addCard("A")
        root.addCard("B")
        val home = root.home()
        assertEquals(listOf("B", "A"), home.model.value.cards.map { it.name })

        home.onCardEditClicked(home.model.value.cards[1].id)
        root.review().onNameChanged("A2")
        root.review().onAddToWalletClicked()
        root.review().onCloseClicked()
        assertEquals(listOf("B", "A2"), home.model.value.cards.map { it.name })
    }

    @Test
    fun reorderingPersists() = runTest {
        val dataDirectory = tempDirectory("data")
        val root = root(services(dataDirectory = dataDirectory))
        listOf("A", "B", "C").forEach { root.addCard(it) }
        val home = root.home()

        home.onCardMoved(fromIndex = 2, toIndex = 0) // A to the top
        assertEquals(listOf("A", "C", "B"), home.model.value.cards.map { it.name })

        val reopened = CardLibrary(dataDirectory.toString(), EmptyCoroutineContext)
        reopened.load()
        assertEquals(listOf("A", "C", "B"), reopened.cards.value.map { it.form.name })
    }

    @Test
    fun searchFiltersByNameAndCodeOnceTheLibraryIsLong() {
        val root = root(services())
        val home = root.home()
        (1..6).forEach { root.addCard("CODE-$it", name = "Card $it") }
        assertEquals(false, home.model.value.isSearchAvailable)

        root.addCard("LIDL-7", name = "Supermarket")
        assertEquals(true, home.model.value.isSearchAvailable)

        home.onSearchQueryChanged("super")
        assertEquals(listOf("Supermarket"), home.model.value.visibleCards.map { it.name })
        assertEquals(false, home.model.value.canReorder)

        home.onSearchQueryChanged("code-3")
        assertEquals(listOf("Card 3"), home.model.value.visibleCards.map { it.name })

        // Reordering is off while filtering.
        home.onCardMoved(0, 3)
        assertEquals("Supermarket", home.model.value.cards.first().name)

        home.onSearchQueryChanged("")
        assertEquals(7, home.model.value.visibleCards.size)
        assertEquals(true, home.model.value.canReorder)
    }

    @Test
    fun walletStatusIsShownWhereThePlatformKnowsIt() {
        val wallet = FakeWalletService()
        val root = root(services(wallet))
        root.addCard("A")
        assertNull(root.home().model.value.cards.single().isInWallet)

        wallet.passesInWallet = emptySet()
        root.addCard("B")
        assertEquals(listOf(false, false), root.home().model.value.cards.map { it.isInWallet })
    }

    @Test
    fun exportedLibraryImportsElsewhereAsNotInWallet() = runTest {
        val source = root(services())
        source.addCard("A")
        source.addCard("B")
        val home = source.home()
        home.onExportClicked()
        val ready = assertIs<BackupState.ExportReady>(home.model.value.backup)
        assertTrue(ready.fileName.startsWith("Walletka-backup-") && ready.fileName.endsWith(".walletka"))
        assertEquals(2, ready.cardCount)

        // The UI copies the file out (save dialog), then reports back.
        val copy = Path(tempDirectory("picked"), "backup.walletka")
        SystemFileSystem.atomicMove(Path(ready.path), copy)
        home.onExportFinished()
        assertEquals(BackupState.Idle, home.model.value.backup)

        val wallet = FakeWalletService().apply { canUpdateInPlace = true }
        val target = root(services(wallet))
        target.home().onImportFileSelected(copy.toString())
        assertEquals(BackupState.Imported(added = 2, updated = 0, skipped = 0), target.home().model.value.backup)
        val cards = target.home().model.value.cards
        assertEquals(listOf("B", "A"), cards.map { it.name })
        assertEquals(listOf(false, false), cards.map { it.isInWallet })

        // Imported cards open as not in the wallet, ready to be added.
        target.home().onCardEditClicked(cards[0].id)
        val editing = assertIs<Model.Editing>(target.review().model.value)
        assertEquals(SyncStatus.NOT_IN_WALLET, editing.sync)
        assertEquals(true, editing.hasChanges)
        target.review().onAddToWalletClicked()
        assertEquals("B", wallet.added.single().name)
        target.review().onCloseClicked()
        assertEquals(listOf(null, false), target.home().model.value.cards.map { it.isInWallet })
    }

    @Test
    fun importingAnInvalidFileReportsAFailure() {
        val root = root(services())
        root.home().onImportFileSelected(Path(tempDirectory("x"), "missing.walletka").toString())
        assertIs<BackupState.Failed>(root.home().model.value.backup)
        root.home().onBackupMessageDismissed()
        assertEquals(BackupState.Idle, root.home().model.value.backup)
    }
}
