package cz.poqet.walletka.component.home

import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.platform.CloudSyncStatus
import cz.poqet.walletka.platform.WalletPlatform
import kotlinx.coroutines.flow.StateFlow

/** The card library and the ways to add a new card. */
interface HomeComponent {

    val model: StateFlow<Model>

    /** The user picked or pasted an image. See [cz.poqet.walletka.platform.ImageStore.import] for [source]. */
    fun onImagePicked(source: String)

    /** Starts a card without a photo: name, code and colour are typed in / picked by hand. */
    fun onEnterManuallyClicked()

    /** Shows a library card, to be scanned. */
    fun onCardClicked(cardId: String)

    /** Opens a library card's editor (above its preview). */
    fun onCardEditClicked(cardId: String)

    /** Deletes a library card (and its pass, where the wallet allows it). */
    fun onCardDeleteClicked(cardId: String)

    /** Filters [Model.visibleCards] by name and code. */
    fun onSearchQueryChanged(query: String)

    /** Moves a card from [fromIndex] to [toIndex] of [Model.cards]; only while [Model.canReorder]. */
    fun onCardMoved(fromIndex: Int, toIndex: Int)

    /** Writes the whole library to a backup file; see [BackupState.ExportReady]. */
    fun onExportClicked()

    /** The system UI is done with the exported file (saved, shared or cancelled); the temporary file goes. */
    fun onExportFinished()

    /** Merges the backup file at [path] (a local copy the component only reads) into the library. */
    fun onImportFileSelected(path: String)

    /** Clears a finished or failed [Model.backup]. */
    fun onBackupMessageDismissed()

    /** Turns syncing the library across devices on or off; see [Model.cloudSync]. */
    fun onCloudSyncToggled(enabled: Boolean)

    /** Syncs the library with the user's other devices now. */
    fun onSyncNowClicked()

    data class Model(
        val walletPlatform: WalletPlatform,
        /** `null` while the availability check is running. */
        val isWalletAvailable: Boolean?,
        /** Cards added through Walletka, in the user's order (new cards on top). */
        val cards: List<CardSummary>,
        /** `false` until the library has been read, so an empty [cards] doesn't flash the "no cards" screen. */
        val isLoaded: Boolean = true,
        /** [cards] matching [searchQuery]. */
        val visibleCards: List<CardSummary> = cards,
        val searchQuery: String = "",
        /** Search is offered once the library is long enough to need it (more than six cards). */
        val isSearchAvailable: Boolean = false,
        /** Cards can be dragged into a new order (not while searching). */
        val canReorder: Boolean = true,
        val backup: BackupState = BackupState.Idle,
        /** `null` where the platform has no library sync (Android relies on its system backup). */
        val cloudSync: CloudSyncStatus? = null,
        /** Why the last sync failed, as the platform describes it. */
        val cloudSyncError: String? = null,
    )

    data class CardSummary(
        val id: String,
        val name: String,
        val color: CardColor,
        val codeFormat: CodeFormat,
        val codeValue: String,
        val banner: StoredImage?,
        /**
         * Whether the pass is in this device's wallet: as the wallet reports it, or where it can't tell (Google Wallet)
         * whether Walletka added it; `null` until known.
         */
        val isInWallet: Boolean? = null,
    )
}
