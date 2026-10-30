package cz.poqet.walletka.component

import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.resume
import cz.poqet.walletka.component.preview.CardPreviewComponent
import cz.poqet.walletka.component.preview.CardPreviewComponent.WalletAction
import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.component.root.DefaultRootComponent
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.WalletError
import cz.poqet.walletka.domain.WalletFailure
import arrow.core.left
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class CardPreviewTest {

    private val lifecycle = LifecycleRegistry()
    private val code = ScannedCode(CodeFormat.QR, "https://coffee.example/card/42")

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun root(wallet: FakeWalletService) =
        DefaultRootComponent(DefaultComponentContext(lifecycle), fakeServices(listOf(code), walletService = wallet)).also { lifecycle.resume() }

    private fun RootComponent.home() = assertIs<RootComponent.Child.Home>(children.value.first()).component

    private fun RootComponent.review() = assertIs<RootComponent.Child.Review>(children.value.last()).component

    private fun RootComponent.preview() = assertIs<RootComponent.Child.Preview>(children.value.last()).component

    private val CardPreviewComponent.showing get() = assertIs<CardPreviewComponent.PreviewModel.Showing>(model.value)

    /** Types a card in and saves it without adding it to the wallet; returns the root showing its preview. */
    private fun savedCard(wallet: FakeWalletService): RootComponent {
        val root = root(wallet)
        root.home().onEnterManuallyClicked()
        root.review().onNameChanged("Gym")
        root.review().onCodeValueChanged("GYM-42")
        root.review().onSaveClicked()
        return root
    }

    @Test
    fun aSavedCardIsShownWithoutGoingToTheWallet() {
        val wallet = FakeWalletService()
        val root = savedCard(wallet)

        assertEquals(emptyList(), wallet.added)
        val preview = root.preview()
        assertEquals("Gym", preview.showing.form.name)
        assertFalse(preview.showing.isInWallet)
        assertEquals(listOf(false), root.home().model.value.cards.map { it.isInWallet })
    }

    @Test
    fun theWalletPassIsAddedAndRemovedFromThePreview() {
        val wallet = FakeWalletService()
        val preview = savedCard(wallet).preview()

        preview.onAddToWalletClicked()
        val pass = wallet.added.single()
        assertEquals("GYM-42", pass.code.value)
        assertTrue(preview.showing.isInWallet)
        assertEquals(WalletAction.Idle, preview.showing.walletAction)

        preview.onRemoveFromWalletClicked()
        assertEquals(listOf(pass.walletId), wallet.removed)
        assertFalse(preview.showing.isInWallet)

        // Added again: the same pass identity, so it replaces rather than duplicates.
        preview.onAddToWalletClicked()
        assertEquals(pass.walletId, wallet.added.last().walletId)
    }

    @Test
    fun aCancelledOrFailedAddLeavesTheCardOutOfTheWallet() {
        val wallet = FakeWalletService(WalletError(WalletFailure.CANCELLED).left())
        val preview = savedCard(wallet).preview()

        preview.onAddToWalletClicked()
        assertFalse(preview.showing.isInWallet)
        assertEquals(WalletAction.Idle, preview.showing.walletAction)

        wallet.result = WalletError(WalletFailure.FAILED, "offline").left()
        preview.onAddToWalletClicked()
        assertEquals(WalletAction.Failed(WalletFailure.FAILED, "offline"), preview.showing.walletAction)
        preview.onWalletMessageDismissed()
        assertEquals(WalletAction.Idle, preview.showing.walletAction)
    }

    @Test
    fun aWalletThatCantRemovePassesSaysSo() {
        val wallet = FakeWalletService().apply { canRemove = false }
        val preview = savedCard(wallet).preview()
        preview.onAddToWalletClicked()

        preview.onRemoveFromWalletClicked()
        assertEquals(WalletAction.RemoveUnsupported, preview.showing.walletAction)
        // The wallet can't tell whether it still has the pass: the user removes it there, and can add it again here.
        assertFalse(preview.showing.isInWallet)

        // A wallet that can tell (Apple Wallet) keeps showing a pass it couldn't remove.
        val appleWallet = FakeWalletService().apply { canRemove = false }
        val applePreview = savedCard(appleWallet).preview()
        applePreview.onAddToWalletClicked()
        appleWallet.passesInWallet = setOf(appleWallet.added.single().walletId)
        applePreview.onRemoveFromWalletClicked()
        assertTrue(applePreview.showing.isInWallet)
    }

    @Test
    fun editingReturnsToThePreviewWithTheChanges() {
        val wallet = FakeWalletService()
        val root = savedCard(wallet)
        root.preview().onEditClicked()
        root.review().onNameChanged("City Gym")
        // Back from the editor (button or swipe): the edits are kept even though the card isn't in the wallet.
        root.onBackClicked()

        assertEquals("City Gym", root.preview().showing.form.name)
        assertEquals("City Gym", root.home().model.value.cards.single().name)
        assertEquals(emptyList(), wallet.added)
    }

    @Test
    fun editsAreKeptWhereTheWalletCantUpdatePassesSilently() {
        val wallet = FakeWalletService().apply { canUpdateInPlace = false }
        val root = savedCard(wallet)
        root.preview().onAddToWalletClicked()
        root.preview().onEditClicked()
        root.review().onNameChanged("City Gym")
        root.onBackClicked()

        assertEquals("City Gym", root.home().model.value.cards.single().name)
    }

    @Test
    fun aNewCardAddedToTheWalletContinuesToItsPreview() {
        val wallet = FakeWalletService()
        val root = root(wallet)
        root.home().onEnterManuallyClicked()
        root.review().onNameChanged("Gym")
        root.review().onCodeValueChanged("GYM-42")
        root.review().onAddToWalletClicked()
        assertIs<Model.Editing>(root.review().model.value)
        root.review().onCloseClicked()

        assertTrue(root.preview().showing.isInWallet)
        assertEquals(1, wallet.added.size)
    }

    @Test
    fun deletingFromThePreviewReturnsToTheLibrary() {
        val wallet = FakeWalletService()
        val root = savedCard(wallet)
        root.preview().onEditClicked()
        root.onBackClicked()
        root.preview().onDeleteClicked()

        assertIs<RootComponent.Child.Home>(root.children.value.single())
        assertEquals(emptyList(), root.home().model.value.cards)
    }
}
