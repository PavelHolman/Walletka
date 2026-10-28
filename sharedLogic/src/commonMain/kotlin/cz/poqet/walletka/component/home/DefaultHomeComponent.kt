package cz.poqet.walletka.component.home

import com.arkivanov.decompose.ComponentContext
import com.arkivanov.essenty.lifecycle.coroutines.coroutineScope
import cz.poqet.walletka.component.home.HomeComponent.Model
import cz.poqet.walletka.platform.WalletService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal class DefaultHomeComponent(
    componentContext: ComponentContext,
    private val walletService: WalletService,
    private val onImagePicked: (source: String) -> Unit,
) : HomeComponent, ComponentContext by componentContext {

    private val scope = coroutineScope(Dispatchers.Main.immediate)
    private val _model = MutableStateFlow(Model(walletPlatform = walletService.platform, isWalletAvailable = null))
    override val model: StateFlow<Model> = _model.asStateFlow()

    init {
        scope.launch {
            val available = walletService.isAvailable()
            _model.update { it.copy(isWalletAvailable = available) }
        }
    }

    override fun onImagePicked(source: String) {
        onImagePicked.invoke(source)
    }
}
