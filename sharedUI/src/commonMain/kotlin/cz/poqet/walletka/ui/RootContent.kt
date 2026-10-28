package cz.poqet.walletka.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.ui.home.HomeContent
import cz.poqet.walletka.ui.review.CardReviewContent

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    WalletkaTheme {
        Children(
            stack = component.stack,
            modifier = modifier,
            animation = stackAnimation(fade() + slide()),
        ) { child ->
            when (val instance = child.instance) {
                is RootComponent.Child.Home -> HomeContent(instance.component)
                is RootComponent.Child.Review -> CardReviewContent(instance.component)
            }
        }
    }
}
