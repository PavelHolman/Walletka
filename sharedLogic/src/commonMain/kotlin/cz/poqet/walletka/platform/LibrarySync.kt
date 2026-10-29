package cz.poqet.walletka.platform

import cz.poqet.walletka.data.CardLibrary
import kotlinx.coroutines.flow.StateFlow

/** State of syncing the card library across the user's devices (iCloud on iOS). */
enum class CloudSyncStatus {
    /** The user turned sync off. */
    OFF,

    /** Not signed in to iCloud (or iCloud is restricted): the library stays on this device. */
    UNAVAILABLE,
    SYNCING,
    UP_TO_DATE,

    /** The last sync failed; it is retried automatically. */
    FAILED,
}

/** Reasons iCloud can't be used that the UI words itself; other errors arrive already localized by the system. */
object CloudSyncReason {
    const val RESTRICTED = "walletka.cloud.restricted"
    const val TEMPORARILY_UNAVAILABLE = "walletka.cloud.temporarilyUnavailable"
}

/**
 * Keeps the [CardLibrary] in sync with the user's other devices. The engine uploads what the library reports through
 * [CardLibrary.changeListener] and hands what it fetches to [CardLibrary.applyRemote], whose merge rules decide.
 */
internal interface LibrarySync {
    val status: StateFlow<CloudSyncStatus>

    /** Why the last sync failed, as the platform describes it; `null` while syncing works. */
    val lastError: StateFlow<String?>

    /** Starts syncing [library] (when enabled); call once. */
    fun start(library: CardLibrary)

    /** Turns sync on or off; remembered across launches. Turning it off keeps the cards on this device. */
    fun setEnabled(enabled: Boolean)

    /** Fetches and sends changes now, e.g. when the user asks for it. */
    fun syncNow()
}
