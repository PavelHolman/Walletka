package cz.poqet.walletka.component.home

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import cz.poqet.walletka.component.home.HomeComponent.CardSummary
import cz.poqet.walletka.component.home.HomeComponent.Model
import cz.poqet.walletka.data.CardLibrary
import cz.poqet.walletka.data.StoredCard
import cz.poqet.walletka.platform.ImageStore
import cz.poqet.walletka.platform.WalletService
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val walletService: WalletService,
    private val library: CardLibrary,
    private val imageStore: ImageStore,
    private val onImagePicked: (source: String) -> Unit,
    private val onCardClicked: (cardId: String) -> Unit,
) : HomeComponent, ComponentContext by componentContext {

    private val scope = coroutineScope(Dispatchers.Main.immediate)
    private val _model = MutableStateFlow(
        Model(walletPlatform = walletService.platform, isWalletAvailable = null, cards = library.cards.value.map { it.toSummary() }),
    )
    override val model: StateFlow<Model> = _model.asStateFlow()

    init {
        scope.launch {
            val available = walletService.isAvailable()
            _model.update { it.copy(isWalletAvailable = available) }
        }
        scope.launch {
            library.load()
            library.cards.collect { cards -> _model.update { it.copy(cards = cards.map { card -> card.toSummary() }) } }
        }
    }

    override fun onImagePicked(source: String) {
        onImagePicked.invoke(source)
    }

    override fun onCardClicked(cardId: String) {
        onCardClicked.invoke(cardId)
    }

    override fun onCardDeleteClicked(cardId: String) {
        scope.launch {
            withContext(NonCancellable) {
                library.deleteCard(cardId, { walletService.removeCard(it) }, imageStore::delete)
            }
        }
    }

    private fun StoredCard.toSummary(): CardSummary {
        val form = form.toForm()
        return CardSummary(
            id = id,
            name = form.name,
            color = form.selectedColor,
            codeFormat = form.selectedCode.format,
            codeValue = form.selectedCode.value,
            banner = form.banner.takeIf { form.isBannerEnabled },
        )
    }
}
