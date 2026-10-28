package cz.poqet.walletka.component.home

import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.coroutines.flow.StateFlow

interface HomeComponent {

    val model: StateFlow<Model>

    /** The user picked or pasted an image. See [cz.poqet.walletka.platform.ImageStore.import] for [source]. */
    fun onImagePicked(source: String)

    data class Model(
        val walletPlatform: WalletPlatform,
        /** `null` while the availability check is running. */
        val isWalletAvailable: Boolean?,
    )
}
