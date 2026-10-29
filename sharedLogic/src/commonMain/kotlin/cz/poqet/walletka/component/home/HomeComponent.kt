package cz.poqet.walletka.component.home

import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.coroutines.flow.StateFlow

/** The card library and the ways to add a new card. */
interface HomeComponent {

    val model: StateFlow<Model>

    /** The user picked or pasted an image. See [cz.poqet.walletka.platform.ImageStore.import] for [source]. */
    fun onImagePicked(source: String)

    /** Opens a library card for editing. */
    fun onCardClicked(cardId: String)

    /** Deletes a library card (and its pass, where the wallet allows it). */
    fun onCardDeleteClicked(cardId: String)

    data class Model(
        val walletPlatform: WalletPlatform,
        /** `null` while the availability check is running. */
        val isWalletAvailable: Boolean?,
        /** Cards added through Walletka, most recently changed first. */
        val cards: List<CardSummary>,
    )

    data class CardSummary(
        val id: String,
        val name: String,
        val color: CardColor,
        val codeFormat: CodeFormat,
        val codeValue: String,
        val banner: StoredImage?,
    )
}
