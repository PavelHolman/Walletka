package cz.poqet.walletka.data

import kotlinx.serialization.Serializable

/**
 * What the library remembers for merging with other copies of it (iCloud, backups): when cards were deleted, so a
 * stale copy can't bring them back, and when the manual order last changed.
 */
@Serializable
internal data class SyncMetadata(
    /** Deleted card id → deletion time (epoch millis). */
    val tombstones: Map<String, Long> = emptyMap(),
    val orderUpdatedAt: Long = 0,
)

/** A change to the library that comes from another copy of it. */
internal sealed interface RemoteChange {
    /** A card as another copy has it; its images must already be in this library. */
    data class Card(val card: StoredCard) : RemoteChange

    data class Deleted(val id: String, val deletedAt: Long) : RemoteChange

    data class Order(val ids: List<String>, val updatedAt: Long) : RemoteChange
}

internal data class LibraryState(val cards: List<StoredCard>, val sync: SyncMetadata = SyncMetadata())

/**
 * The merge rules: the newer version of a card wins (by [StoredCard.updatedAt] against the other copy's version or
 * deletion time), and so does the newer manual order. Cards that are new here go after the existing ones, and are
 * marked as not in this device's wallet.
 */
internal object LibraryMerge {

    class Result(
        val state: LibraryState,
        /** Versions of cards that were replaced or deleted, so their images can be dropped. */
        val dropped: List<StoredCard>,
        /** Ids (of cards or tombstones) that are newer here than in the change: the other copy needs them. */
        val newerHere: Set<String>,
        /** The order here is newer than the one in the change. */
        val isOrderNewerHere: Boolean,
        /** Cards added or replaced by the change. */
        val accepted: Set<String>,
    )

    /**
     * Applies [changes] to [state]. With [revive], a card deleted here comes back (stamped [now], so it is newer
     * than its deletion everywhere): the user explicitly restored it from a backup.
     */
    fun apply(state: LibraryState, changes: List<RemoteChange>, revive: Boolean = false, now: Long = 0): Result {
        val cards = state.cards.toMutableList()
        val tombstones = state.sync.tombstones.toMutableMap()
        val dropped = mutableListOf<StoredCard>()
        val newerHere = mutableSetOf<String>()
        val accepted = mutableSetOf<String>()
        var orderUpdatedAt = state.sync.orderUpdatedAt
        var order: List<String>? = null
        var isOrderNewerHere = false

        for (change in changes) {
            when (change) {
                is RemoteChange.Card -> {
                    var remote = change.card
                    val deletedAt = tombstones[remote.id]
                    if (deletedAt != null && deletedAt >= remote.updatedAt) {
                        if (!revive) {
                            newerHere += remote.id
                            continue
                        }
                        remote = remote.copy(updatedAt = maxOf(now, deletedAt + 1))
                    }
                    tombstones.remove(remote.id)
                    val index = cards.indexOfFirst { it.id == remote.id }
                    if (index < 0) {
                        cards += remote.copy(addedToWallet = false)
                        accepted += remote.id
                        continue
                    }
                    val local = cards[index]
                    when {
                        local.updatedAt > remote.updatedAt -> newerHere += remote.id
                        local.updatedAt < remote.updatedAt -> {
                            // Whether the pass is in this device's wallet doesn't travel with the card.
                            cards[index] = remote.copy(addedToWallet = local.addedToWallet)
                            dropped += local
                            accepted += remote.id
                        }
                    }
                }

                is RemoteChange.Deleted -> {
                    val local = cards.firstOrNull { it.id == change.id }
                    if (local != null && local.updatedAt > change.deletedAt) {
                        newerHere += change.id
                        continue
                    }
                    if (local != null) {
                        cards -= local
                        dropped += local
                    }
                    tombstones[change.id] = maxOf(change.deletedAt, tombstones[change.id] ?: 0)
                }

                is RemoteChange.Order -> when {
                    change.updatedAt > orderUpdatedAt -> {
                        order = change.ids
                        orderUpdatedAt = change.updatedAt
                        isOrderNewerHere = false
                    }
                    change.updatedAt < orderUpdatedAt -> isOrderNewerHere = true
                }
            }
        }

        val ordered = order?.let { ordered(cards, it) } ?: cards
        return Result(
            state = LibraryState(ordered, SyncMetadata(tombstones, orderUpdatedAt)),
            dropped = dropped,
            newerHere = newerHere,
            isOrderNewerHere = isOrderNewerHere,
            accepted = accepted,
        )
    }

    /** [cards] in the order of [ids]; cards the order doesn't know yet (added since) stay on top. */
    fun ordered(cards: List<StoredCard>, ids: List<String>): List<StoredCard> {
        val position = ids.withIndex().associate { (index, id) -> id to index }
        val (known, unknown) = cards.partition { it.id in position }
        return unknown + known.sortedBy { position.getValue(it.id) }
    }
}
