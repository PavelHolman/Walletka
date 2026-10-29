package cz.poqet.walletka.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
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
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
        assertEquals("/stored/banner.png", editing.form.banner?.path)
    }

    @Test
    fun noCodeFails() {
        val root = root(fakeServices(emptyList()))
        root.onImageReceived("photo.jpg")
        assertEquals(Model.Failed(AnalysisFailure.NO_CODE_FOUND), root.review().model.value)
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

        assertEquals(Submission.Added, assertIs<Model.Editing>(review.model.value).submission)
        val card = wallet.added.single()
        assertEquals("My Coffee", card.name)
        assertEquals(null, card.banner)

        review.onCloseClicked()
        assertIs<RootComponent.Child.Home>(root.children.value.single())
        assertEquals(listOf("/stored/photo.jpg", "/stored/banner.png"), store.deleted.map { it.path })
    }

    @Test
    fun newShareReplacesCurrentReview() {
        val root = root(fakeServices(listOf(code)))
        root.onImageReceived("first.jpg")
        root.onImageReceived("second.jpg")
        assertEquals(2, root.children.value.size)
        assertEquals("/stored/second.jpg", assertIs<Model.Editing>(root.review().model.value).form.photo.path)
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
}
