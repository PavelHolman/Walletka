package cz.poqet.walletka.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.slide
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import cz.poqet.walletka.component.root.RootComponent
import cz.poqet.walletka.ui.home.HomeContent
import cz.poqet.walletka.ui.preview.CardPreviewContent
import cz.poqet.walletka.ui.review.CardReviewContent

@Composable
fun RootContent(component: RootComponent, modifier: Modifier = Modifier) {
    WalletkaTheme {
        Children(
            stack = component.stack,
            modifier = modifier,
            animation = stackAnimation(fade() + slide()),
        ) { child ->
            // On tablets and unfolded foldables, a readable column in the middle instead of edge-to-edge forms;
            // the background still fills the window.
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
                Box(Modifier.widthIn(max = 640.dp).fillMaxSize()) {
                    when (val instance = child.instance) {
                        is RootComponent.Child.Home -> HomeContent(instance.component)
                        is RootComponent.Child.Review -> CardReviewContent(instance.component)
                        is RootComponent.Child.Preview -> CardPreviewContent(instance.component)
                    }
                }
            }
        }
    }
}
