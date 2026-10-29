package cz.poqet.walletka.component.root

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.ChildStack
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.childStack
import com.arkivanov.decompose.router.stack.navigate
import com.arkivanov.decompose.router.stack.pop
import com.arkivanov.decompose.router.stack.pushToFront
import com.arkivanov.decompose.router.stack.popTo
import com.arkivanov.decompose.router.stack.replaceCurrent
import com.arkivanov.decompose.value.Value
import cz.poqet.walletka.component.home.DefaultHomeComponent
import cz.poqet.walletka.component.review.DefaultCardReviewComponent
import cz.poqet.walletka.component.root.RootComponent.Child
import cz.poqet.walletka.component.toStateFlow
import cz.poqet.walletka.data.BannerCropper
import cz.poqet.walletka.data.CardLibrary
import cz.poqet.walletka.component.review.ReviewDependencies
import cz.poqet.walletka.component.review.ReviewSource
import cz.poqet.walletka.data.ImageAnalyzer
import cz.poqet.walletka.data.LibraryBackup
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
    private val bannerCropper = BannerCropper(services.imageProcessor, services.imageStore)
    private val analyzer = ImageAnalyzer(
        barcodeDetector = services.barcodeDetector,
        textRecognizer = services.textRecognizer,
        imageProcessor = services.imageProcessor,
        bannerCropper = bannerCropper,
    )
    private val library = CardLibrary(services.dataDirectory, services.ioContext)
        .also { library -> services.librarySync?.start(library) }
    private val backup = LibraryBackup(library, services.walletService.platform, services.ioContext)
    private val reviewDependencies = ReviewDependencies(
        imageStore = services.imageStore,
        imageProcessor = services.imageProcessor,
        analyzer = analyzer,
        bannerCropper = bannerCropper,
        walletService = services.walletService,
        library = library,
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
                library = library,
                imageStore = services.imageStore,
                onImagePicked = ::onImageReceived,
                onCardClicked = { cardId -> navigation.pushToFront(Config.Card(cardId)) },
                backup = backup,
                exportDirectory = "${services.dataDirectory}/exports",
                librarySync = services.librarySync,
                ioContext = services.ioContext,
            ),
        )

        is Config.Review -> review(componentContext, ReviewSource.Photo(config.source))

        is Config.Card -> review(componentContext, ReviewSource.LibraryCard(config.cardId))
    }

    private fun review(componentContext: ComponentContext, source: ReviewSource) = Child.Review(
        DefaultCardReviewComponent(
            componentContext = componentContext,
            source = source,
            dependencies = reviewDependencies,
            onFinished = { navigation.popTo(index = 0) },
            onOpenCard = { cardId -> navigation.replaceCurrent(Config.Card(cardId)) },
        ),
    )

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

        @Serializable
        data class Card(val cardId: String) : Config
    }
}
