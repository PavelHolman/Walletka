package cz.poqet.walletka.component.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.value.Value
import cz.poqet.walletka.component.home.DefaultHomeComponent
import cz.poqet.walletka.component.review.DefaultCardReviewComponent
import cz.poqet.walletka.component.root.RootComponent.Child
import cz.poqet.walletka.component.toStateFlow
import cz.poqet.walletka.data.ImageAnalyzer
import cz.poqet.walletka.platform.PlatformServices
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal class DefaultRootComponent(
    componentContext: ComponentContext,
    private val services: PlatformServices,
) : RootComponent, ComponentContext by componentContext {

    private val navigation = StackNavigation<Config>()
    private val analyzer = ImageAnalyzer(
        barcodeDetector = services.barcodeDetector,
        textRecognizer = services.textRecognizer,
        imageProcessor = services.imageProcessor,
        imageStore = services.imageStore,
    )

    override val stack: Value<ChildStack<*, Child>> = childStack(
        source = navigation,
        serializer = Config.serializer(),
        initialConfiguration = Config.Home,
        handleBackButton = true,
        childFactory = ::child,
    )

    override val children: StateFlow<List<Child>> =
        stack.toStateFlow(lifecycle) { stack -> stack.items.map { it.instance } }

    private fun child(config: Config, componentContext: ComponentContext): Child = when (config) {
        Config.Home -> Child.Home(
            DefaultHomeComponent(
                componentContext = componentContext,
                walletService = services.walletService,
                onImagePicked = ::onImageReceived,
            ),
        )

        is Config.Review -> Child.Review(
            DefaultCardReviewComponent(
                componentContext = componentContext,
                source = config.source,
                imageStore = services.imageStore,
                analyzer = analyzer,
                walletService = services.walletService,
                onFinished = navigation::pop,
            ),
        )
    }

    @OptIn(ExperimentalUuidApi::class)
    override fun onImageReceived(source: String) {
        // A new image always replaces the card being reviewed.
        navigation.navigate { listOf(Config.Home, Config.Review(source, id = Uuid.random().toString())) }
    }

    override fun onBackClicked() {
        navigation.pop()
    }

    override fun onBackClicked(toIndex: Int) {
        navigation.popTo(index = toIndex)
    }

    @Serializable
    private sealed interface Config {
        @Serializable
        data object Home : Config

        @Serializable
        data class Review(val source: String, val id: String) : Config
    }
}
