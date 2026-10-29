package cz.poqet.walletka.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import cz.poqet.walletka.component.review.CardReviewComponent
import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.component.review.SavedCardForm
import cz.poqet.walletka.component.root.DefaultRootComponent
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.data.CardLibrary
import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.platform.PlatformServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The pass logo and the optional details (back of the pass) through the review flow and the library. */
@OptIn(ExperimentalCoroutinesApi::class)
class PassDetailsTest {

    private val lifecycle = LifecycleRegistry()
    private val code = ScannedCode(CodeFormat.QR, "https://coffee.example/card/42", NormalizedRect(0.3f, 0.6f, 0.7f, 0.9f))
    private val details = CardDetails(memberNumber = "42", memberName = "Jana", expiryDate = "2027-05-31", notes = "Hi")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun root(services: PlatformServices) =
        DefaultRootComponent(DefaultComponentContext(lifecycle), services).also { lifecycle.resume() }

    private fun RootComponent.review() = assertIs<RootComponent.Child.Review>(children.value.last()).component

    private val CardReviewComponent.form get() = assertIs<Model.Editing>(model.value).form

    @Test
    fun logoIsOffByDefaultAndCutWhenTurnedOn() {
        val wallet = FakeWalletService()
        val store = FakeImageStore()
        val root = root(fakeServices(listOf(code), imageStore = store, walletService = wallet))
        root.onImageReceived("photo.jpg")
        val review = root.review()
        assertFalse(review.form.logo.isEnabled)
        assertNull(review.form.logo.image)
        assertEquals(CardDetails.Empty, review.form.details)

        review.onLogoToggled(true)
        val logo = assertNotNull(review.form.logo.activeImage)
        review.onDetailsChanged(details)
        review.onAddToWalletClicked()

        val card = wallet.added.single()
        assertEquals(logo, card.logo)
        assertEquals(details, card.details)

        // Turning it off keeps the cut image for later, but the pass no longer shows it.
        review.onLogoToggled(false)
        assertNotNull(review.form.logo.image)
        assertNull(review.form.logo.activeImage)
    }

    @Test
    fun movingTheLogoRecropsIt() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val store = FakeImageStore()
        val root = root(fakeServices(listOf(code), imageStore = store))
        root.onImageReceived("photo.jpg")
        testScheduler.advanceUntilIdle()
        val review = root.review()
        review.onLogoToggled(true)
        testScheduler.advanceUntilIdle()
        val first = assertNotNull(review.form.logo.image)

        review.onLogoRegionChanged(NormalizedRect(0f, 0.4f, 0.5f, 0.5f))
        testScheduler.advanceUntilIdle()
        val moved = review.form.logo
        // The Google logo is square: the 5:1 region gets reshaped.
        assertEquals(1f, moved.region.width * review.form.photoAspectRatio / moved.region.height, 0.01f)
        assertTrue(moved.image != first)
        assertTrue(first in store.deleted)
    }

    @Test
    fun detailEditsOfALibraryCardSyncAutomatically() = runTest {
        val wallet = FakeWalletService()
        val root = libraryCardWithAutoSync(wallet)
        val edit = root.review()

        edit.onDetailsChanged(details)
        assertEquals(CardReviewComponent.SyncStatus.PENDING, assertIs<Model.Editing>(edit.model.value).sync)
        testScheduler.advanceUntilIdle()
        assertEquals(details, wallet.updated.single().details)
        assertEquals(CardReviewComponent.SyncStatus.SYNCED, assertIs<Model.Editing>(edit.model.value).sync)

        // Whitespace alone isn't a change worth syncing.
        edit.onDetailsChanged(details.copy(notes = "Hi  "))
        assertEquals(false, assertIs<Model.Editing>(edit.model.value).hasChanges)
    }

    @Test
    fun logoOfALibraryCardSyncsAndIsKeptInTheLibrary() = runTest {
        val wallet = FakeWalletService()
        val root = libraryCardWithAutoSync(wallet)
        val edit = root.review()

        edit.onLogoToggled(true)
        testScheduler.advanceUntilIdle()

        val synced = wallet.updated.single()
        assertNotNull(synced.logo)
        val home = assertIs<RootComponent.Child.Home>(root.children.value.first()).component
        val stored = edit.form.logo.image!!
        assertTrue(stored.path.contains("library"), stored.path)
        assertTrue(SystemFileSystem.exists(Path(stored.path)))
        assertEquals(1, home.model.value.cards.size)
    }

    @Test
    fun detailsAndLogoSurviveTheLibrary() = runTest {
        val dataDirectory = tempDirectory("data")
        val root = root(fakeServices(listOf(code), dataDirectory = dataDirectory))
        root.onImageReceived("photo.jpg")
        val review = root.review()
        review.onLogoToggled(true)
        review.onDetailsChanged(details)
        review.onAddToWalletClicked()
        val saved = review.form

        val reopened = CardLibrary(dataDirectory.toString(), EmptyCoroutineContext)
        reopened.load()
        val form = reopened.cards.value.single().form.toForm()
        assertEquals(details, form.details)
        assertEquals(saved.logo, form.logo)
        assertTrue(reopened.isLibraryImage(form.logo.image!!))
    }

    @Test
    fun libraryFromBeforeLogosAndDetailsStillLoads() = runTest {
        val dataDirectory = tempDirectory("data")
        val library = Path(dataDirectory, "library")
        SystemFileSystem.createDirectories(library)
        SystemFileSystem.sink(Path(library, "cards.json")).buffered().use {
            it.writeString(
                """[{"id":"c1","walletId":"w1","createdAt":1,"updatedAt":2,"form":{"photo":"photo.jpg","name":"Old Card",""" +
                    """"nameSuggestions":[],"codes":[{"format":"QR","value":"x","bounds":null}],"selectedCodeIndex":0,""" +
                    """"colors":[1193046],"selectedColorIndex":0,"suggestedColorCount":1,"banner":null,"isBannerEnabled":false,""" +
                    """"bannerRegion":[0.0,0.0,1.0,0.3],"photoAspectRatio":0.75,"walletPlatform":"APPLE_WALLET","cardId":"c1"}}]""",
            )
        }
        val reopened = CardLibrary(dataDirectory.toString(), EmptyCoroutineContext)
        reopened.load()
        val form = reopened.cards.value.single().form.toForm()
        assertEquals("Old Card", form.name)
        assertEquals(CardDetails.Empty, form.details)
        assertFalse(form.logo.isEnabled)
        assertEquals(form.logoViewport.clamp(form.logo.region), form.logo.region)
        // …and round-trips through the saved state unchanged.
        assertEquals(form, SavedCardForm.of(form).toForm())
    }

    private fun TestScope.libraryCardWithAutoSync(wallet: FakeWalletService): RootComponent {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
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
        return root
    }
}
