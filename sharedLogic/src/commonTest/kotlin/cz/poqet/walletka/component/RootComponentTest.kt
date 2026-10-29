package cz.poqet.walletka.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import cz.poqet.walletka.component.review.CardReviewComponent
import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.component.review.CardReviewComponent.Submission
import cz.poqet.walletka.component.root.DefaultRootComponent
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class RootComponentTest {

    private val lifecycle = LifecycleRegistry()
    private val code = ScannedCode(CodeFormat.QR, "https://coffee.example/card/42", NormalizedRect(0.3f, 0.6f, 0.7f, 0.9f))

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun root(services: cz.poqet.walletka.platform.PlatformServices) =
        DefaultRootComponent(DefaultComponentContext(lifecycle), services).also { lifecycle.resume() }

    private fun RootComponent.review() = assertIs<RootComponent.Child.Review>(children.value.last()).component

    @Test
    fun sharedImageOpensReviewWithSuggestion() {
        val root = root(fakeServices(listOf(code), lines = listOf(TextLine("COFFEE CLUB", NormalizedRect(0f, 0.05f, 1f, 0.2f)))))

        root.onImageReceived("photo.jpg")

        assertEquals(2, root.children.value.size)
        val editing = assertIs<Model.Editing>(root.review().model.value)
        assertEquals("Coffee Club", editing.form.name)
        assertEquals(listOf("Coffee Club", "Coffee"), editing.form.nameSuggestions)
        assertEquals(code, editing.form.selectedCode)
        assertTrue(editing.form.banner!!.path.endsWith("banner0.png"))
    }

    @Test
    fun photoWithoutCodeIsEditedWithACodeToTypeIn() {
        val wallet = FakeWalletService()
        val store = FakeImageStore()
        val root = root(fakeServices(emptyList(), imageStore = store, walletService = wallet))
        root.onImageReceived("photo.jpg")
        val review = root.review()

        val form = assertIs<Model.Editing>(review.model.value).form
        assertTrue(form.isCodeTyped)
        assertEquals("", form.selectedCode.value)
        assertEquals(store.path("photo.jpg"), form.photo?.path) // kept for the banner and colours
        assertTrue(form.banner != null)
        assertEquals(false, form.isValid)

        review.onNameChanged("Gym")
        review.onCodeValueChanged("4006381333931")
        review.onCodeFormatSelected(CodeFormat.EAN_13)
        review.onAddToWalletClicked()

        assertEquals(ScannedCode(CodeFormat.EAN_13, "4006381333931"), wallet.added.single().code)
    }

    @Test
    fun detectedCodesKeepTheirFormat() {
        val root = root(fakeServices(listOf(code)))
        root.onImageReceived("photo.jpg")
        root.review().onCodeFormatSelected(CodeFormat.EAN_13)
        assertEquals(CodeFormat.QR, assertIs<Model.Editing>(root.review().model.value).form.selectedCode.format)
    }

    @Test
    fun manualCardIsAddedToTheLibraryWithoutAPhoto() = kotlinx.coroutines.test.runTest {
        val wallet = FakeWalletService()
        val dataDirectory = tempDirectory("data")
        val root = root(fakeServices(listOf(code), walletService = wallet, dataDirectory = dataDirectory))
        val home = assertIs<RootComponent.Child.Home>(root.children.value.single()).component

        home.onEnterManuallyClicked()
        val review = root.review()
        val form = assertIs<Model.Editing>(review.model.value).form
        assertEquals(null, form.photo)
        assertEquals(null, form.banner)
        assertTrue(form.isCodeTyped)
        assertEquals(cz.poqet.walletka.data.ColorPalette.Defaults, form.colors)

        review.onNameChanged("Library card")
        review.onCodeValueChanged("LIB-77")
        review.onCodeFormatSelected(CodeFormat.QR)
        review.onColorSelected(2)
        review.onColorPreviewRequested(0.5f, 0.5f) // no photo to pick from
        review.onBannerRegionChanged(NormalizedRect(0f, 0f, 0.5f, 0.2f))
        assertEquals(null, assertIs<Model.Editing>(review.model.value).colorPreview)
        review.onAddToWalletClicked()

        val card = wallet.added.single()
        assertEquals(ScannedCode(CodeFormat.QR, "LIB-77"), card.code)
        assertEquals(cz.poqet.walletka.data.ColorPalette.Defaults[2], card.backgroundColor)
        assertEquals(null, card.banner)
        review.onCloseClicked()

        val summary = home.model.value.cards.single()
        assertEquals("Library card", summary.name)
        val reopened = cz.poqet.walletka.data.CardLibrary(dataDirectory.toString(), kotlin.coroutines.EmptyCoroutineContext)
        reopened.load()
        assertEquals(null, reopened.cards.value.single().form.photo)

        // Edited again later from the library, and deleted.
        home.onCardClicked(summary.id)
        val edit = assertIs<Model.Editing>(root.review().model.value)
        assertEquals(null, edit.form.photo)
        assertTrue(edit.form.isCodeTyped)
        assertEquals(false, edit.hasChanges)
        root.review().onDeleteClicked()
        assertEquals(emptyList(), home.model.value.cards)
    }

    @Test
    fun unreadableImageFails() {
        val root = root(fakeServices(listOf(code)))
        root.onImageReceived("broken")
        assertEquals(Model.Failed(AnalysisFailure.IMAGE_UNREADABLE), root.review().model.value)
    }

    @Test
    fun addsEditedCardToWalletAndCleansUpOnClose() {
        val wallet = FakeWalletService()
        val store = FakeImageStore()
        val root = root(fakeServices(listOf(code), imageStore = store, walletService = wallet))
        root.onImageReceived("photo.jpg")
        val review = root.review()

        review.onNameChanged("My Coffee")
        review.onBannerToggled(false)
        review.onAddToWalletClicked()

        assertEquals(Submission.Added("wallet://card", replacedExisting = false), assertIs<Model.Editing>(review.model.value).submission)
        val card = wallet.added.single()
        assertEquals("My Coffee", card.name)
        assertEquals(null, card.banner)

        review.onCloseClicked()
        assertIs<RootComponent.Child.Home>(root.children.value.single())
        // The card is in the library now: its images were moved there, the cache copies dropped.
        assertEquals(listOf(store.path("photo.jpg"), store.path("banner0.png")), store.deleted.map { it.path })
        val summary = assertIs<RootComponent.Child.Home>(root.children.value.single()).component.model.value.cards.single()
        assertEquals("My Coffee", summary.name)
        assertEquals(null, summary.banner)
    }

    @Test
    fun newShareReplacesCurrentReview() {
        val store = FakeImageStore()
        val root = root(fakeServices(listOf(code), imageStore = store))
        root.onImageReceived("first.jpg")
        root.onImageReceived("second.jpg")
        assertEquals(2, root.children.value.size)
        assertEquals(store.path("second.jpg"), assertIs<Model.Editing>(root.review().model.value).form.photo?.path)
    }

    @Test
    fun movingTheBannerRecropsIt() = kotlinx.coroutines.test.runTest {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        val store = FakeImageStore()
        val root = root(fakeServices(listOf(code), imageStore = store))
        root.onImageReceived("photo.jpg")
        testScheduler.advanceUntilIdle()
        val review = root.review()
        val before = assertIs<Model.Editing>(review.model.value).form

        review.onBannerRegionChanged(cz.poqet.walletka.domain.NormalizedRect(0f, 0.5f, 0.5f, 0.6f))
        val moved = assertIs<Model.Editing>(review.model.value).form
        kotlin.test.assertNotEquals(before.bannerRegion, moved.bannerRegion)

        testScheduler.advanceUntilIdle()
        val recropped = assertIs<Model.Editing>(review.model.value).form
        assertEquals(listOf(before.banner!!.path), store.deleted.map { it.path })
        assertEquals(moved.bannerRegion, recropped.bannerRegion)
    }

    @Test
    fun cancellingTheWalletSheetReturnsToTheForm() {
        val wallet = FakeWalletService(result = arrow.core.Either.Left(cz.poqet.walletka.domain.WalletError(cz.poqet.walletka.domain.WalletFailure.CANCELLED)))
        val root = root(fakeServices(listOf(code), walletService = wallet))
        root.onImageReceived("photo.jpg")
        root.review().onAddToWalletClicked()
        assertEquals(Submission.Idle, assertIs<Model.Editing>(root.review().model.value).submission)
    }

    @Test
    fun customAndPhotoColoursReplaceTheCustomSlot() {
        val root = root(fakeServices(listOf(code)))
        root.onImageReceived("photo.jpg")
        val review = root.review()
        val suggested = assertIs<Model.Editing>(review.model.value).form.colors

        review.onCustomColorSelected(cz.poqet.walletka.domain.CardColor(10, 20, 30))
        review.onColorPreviewRequested(0.5f, 0.5f)
        // Previewing doesn't touch the card…
        assertEquals(cz.poqet.walletka.domain.CardColor(10, 20, 30), assertIs<Model.Editing>(review.model.value).form.selectedColor)
        assertEquals(cz.poqet.walletka.domain.CardColor(26, 35, 126), assertIs<Model.Editing>(review.model.value).colorPreview)
        // …confirming does.
        review.onColorPreviewConfirmed()
        assertEquals(null, assertIs<Model.Editing>(review.model.value).colorPreview)

        val form = assertIs<Model.Editing>(review.model.value).form
        assertEquals(suggested + cz.poqet.walletka.domain.CardColor(26, 35, 126), form.colors) // FakeImageProcessor's pixels
        assertEquals(form.suggestedColorCount, form.selectedColorIndex)
        assertTrue(form.isCustomColorSelected)
    }

    @Test
    fun libraryCardIsEditedAndReissuedWithTheSameSerial() {
        val wallet = FakeWalletService()
        val dataDirectory = tempDirectory("data")
        val root = root(fakeServices(listOf(code), walletService = wallet, dataDirectory = dataDirectory))
        root.onImageReceived("photo.jpg")
        root.review().onAddToWalletClicked()
        root.review().onCloseClicked()

        val home = assertIs<RootComponent.Child.Home>(root.children.value.single()).component
        home.onCardClicked(home.model.value.cards.single().id)
        val edit = root.review()
        val form = assertIs<Model.Editing>(edit.model.value).form
        assertTrue(form.isLibraryCard)

        assertEquals(false, assertIs<Model.Editing>(edit.model.value).hasChanges)
        edit.onNameChanged("Renamed")
        edit.onCodeValueChanged("NEW-123")
        assertEquals(true, assertIs<Model.Editing>(edit.model.value).hasChanges)
        edit.onAddToWalletClicked()

        val (first, second) = wallet.added
        assertEquals(first.walletId, second.walletId)
        assertEquals("NEW-123", second.code.value)
        assertEquals("Renamed", home.model.value.cards.single().name)
        assertEquals(false, assertIs<Model.Editing>(edit.model.value).hasChanges)

        edit.onDeleteClicked()
        assertEquals(listOf(first.walletId), wallet.removed)
        assertEquals(emptyList(), home.model.value.cards)
        assertIs<RootComponent.Child.Home>(root.children.value.single())
    }

    @Test
    fun libraryPersistsAcrossLaunches() = kotlinx.coroutines.test.runTest {
        val dataDirectory = tempDirectory("data")
        val root = root(fakeServices(listOf(code), dataDirectory = dataDirectory))
        root.onImageReceived("photo.jpg")
        root.review().onAddToWalletClicked()

        val reopened = cz.poqet.walletka.data.CardLibrary(dataDirectory.toString(), kotlin.coroutines.EmptyCoroutineContext)
        reopened.load()
        assertEquals(1, reopened.cards.value.size)
        assertTrue(reopened.isLibraryImage(cz.poqet.walletka.domain.StoredImage(reopened.cards.value.single().form.photo!!)))
    }

    @Test
    fun cardsCanBeDeletedFromTheLibraryList() {
        val wallet = FakeWalletService()
        val root = root(fakeServices(listOf(code), walletService = wallet))
        root.onImageReceived("photo.jpg")
        root.review().onAddToWalletClicked()
        root.review().onCloseClicked()

        val home = assertIs<RootComponent.Child.Home>(root.children.value.single()).component
        home.onCardDeleteClicked(home.model.value.cards.single().id)

        assertEquals(emptyList(), home.model.value.cards)
        assertEquals(wallet.added.map { it.walletId }, wallet.removed)
    }

    @Test
    fun libraryStoresImagePathsRelativeToItsFolder() = kotlinx.coroutines.test.runTest {
        val dataDirectory = tempDirectory("data")
        val root = root(fakeServices(listOf(code), dataDirectory = dataDirectory))
        root.onImageReceived("photo.jpg")
        root.review().onAddToWalletClicked()

        val json = SystemFileSystem.source(Path(dataDirectory, "library", "cards.json")).buffered().use { it.readString() }
        assertTrue("\"photo\":\"photo.jpg\"" in json, json)

        // Moving the whole data directory (as a container change would) keeps the images reachable.
        val reopened = cz.poqet.walletka.data.CardLibrary(dataDirectory.toString(), kotlin.coroutines.EmptyCoroutineContext)
        reopened.load()
        val photo = reopened.cards.value.single().form.photo!!
        assertTrue(SystemFileSystem.exists(Path(photo)), photo)
    }

    private fun kotlinx.coroutines.test.TestScope.libraryCardWithAutoSync(wallet: FakeWalletService): Pair<RootComponent, cz.poqet.walletka.component.home.HomeComponent> {
        Dispatchers.setMain(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler))
        wallet.canUpdateInPlace = true
        val root = root(fakeServices(listOf(code), walletService = wallet))
        root.onImageReceived("photo.jpg")
        testScheduler.advanceUntilIdle()
        root.review().onAddToWalletClicked()
        testScheduler.advanceUntilIdle()
        root.review().onCloseClicked()
        testScheduler.advanceUntilIdle()
        val home = assertIs<RootComponent.Child.Home>(root.children.value.single()).component
        home.onCardClicked(home.model.value.cards.single().id)
        testScheduler.advanceUntilIdle()
        return root to home
    }

    @Test
    fun libraryCardEditsSyncAutomatically() = kotlinx.coroutines.test.runTest {
        val wallet = FakeWalletService()
        val (root, home) = libraryCardWithAutoSync(wallet)
        val edit = root.review()
        assertEquals(CardReviewComponent.SyncStatus.SYNCED, assertIs<Model.Editing>(edit.model.value).sync)

        edit.onNameChanged("Renamed")
        assertEquals(CardReviewComponent.SyncStatus.PENDING, assertIs<Model.Editing>(edit.model.value).sync)
        testScheduler.advanceUntilIdle()

        val synced = assertIs<Model.Editing>(edit.model.value)
        assertEquals(CardReviewComponent.SyncStatus.SYNCED, synced.sync)
        assertEquals(false, synced.hasChanges)
        assertEquals("Renamed", wallet.updated.single().name)
        assertEquals(wallet.added.single().walletId, wallet.updated.single().walletId)
        assertEquals("Renamed", home.model.value.cards.single().name)
    }

    @Test
    fun closingWithPendingEditsStillSyncs() = kotlinx.coroutines.test.runTest {
        val wallet = FakeWalletService()
        val (root, home) = libraryCardWithAutoSync(wallet)
        root.review().onNameChanged("Quick edit")
        root.review().onCloseClicked()
        testScheduler.advanceUntilIdle()

        assertEquals("Quick edit", wallet.updated.single().name)
        assertEquals("Quick edit", home.model.value.cards.single().name)
    }

    @Test
    fun removedPassIsReportedAsNotInWallet() = kotlinx.coroutines.test.runTest {
        val wallet = FakeWalletService()
        val (root, _) = libraryCardWithAutoSync(wallet)
        wallet.inWallet = false
        root.review().onNameChanged("Renamed")
        testScheduler.advanceUntilIdle()
        assertEquals(CardReviewComponent.SyncStatus.NOT_IN_WALLET, assertIs<Model.Editing>(root.review().model.value).sync)
    }
}
