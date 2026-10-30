package cz.poqet.walletka.component.root

import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.backhandler.BackHandlerOwner
import cz.poqet.walletka.component.home.HomeComponent
import cz.poqet.walletka.component.preview.CardPreviewComponent
import cz.poqet.walletka.component.review.CardReviewComponent
import kotlinx.coroutines.flow.StateFlow

interface RootComponent : BackHandlerOwner {

    /** Navigation stack, consumed by Compose through Decompose's `Children`. */
    val stack: Value<ChildStack<*, Child>>

    /** The same stack (bottom first, active child last) as a typed flow, consumed by SwiftUI's `NavigationStack`. */
    val children: StateFlow<List<Child>>

    /** An image was shared to the app or opened with it. See [cz.poqet.walletka.platform.ImageStore.import]. */
    fun onImageReceived(source: String)

    fun onBackClicked()

    /** Pops back to the child at [toIndex], e.g. after a swipe-back gesture in a native navigation stack. */
    fun onBackClicked(toIndex: Int)

    sealed interface Child {
        class Home(val component: HomeComponent) : Child
        /** A new card being reviewed, or a library card being edited. */
        class Review(val component: CardReviewComponent) : Child

        /** A library card shown to be scanned. */
        class Preview(val component: CardPreviewComponent) : Child
    }
}
