package cz.poqet.walletka.data

import cz.poqet.walletka.component.review.SavedCardForm
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.platform.WalletPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

internal fun storedCard(id: String, updatedAt: Long = 1, name: String = id, code: String = "code-$id", photo: String? = "$id.jpg") = StoredCard(
    id = id,
    walletId = "wallet-$id",
    createdAt = 1,
    updatedAt = updatedAt,
    form = SavedCardForm(
        photo = photo,
        name = name,
        nameSuggestions = emptyList(),
        codes = listOf(SavedCardForm.Code(CodeFormat.QR, code, null)),
        selectedCodeIndex = 0,
        colors = listOf(0x123456),
        selectedColorIndex = 0,
        suggestedColorCount = 1,
        banner = null,
        isBannerEnabled = false,
        bannerRegion = listOf(0f, 0f, 1f, 0.3f),
        photoAspectRatio = 1.5f,
        walletPlatform = WalletPlatform.APPLE_WALLET,
        cardId = id,
    ),
)

class LibraryMergeTest {

    private fun state(vararg cards: StoredCard, sync: SyncMetadata = SyncMetadata()) = LibraryState(cards.toList(), sync)

    @Test
    fun manualCardWithoutPhotoMergesAndKeepsItsTypedCode() {
        val manual = storedCard("m", updatedAt = 5, photo = null).let { it.copy(form = it.form.copy(isCodeTyped = true)) }
        val result = LibraryMerge.apply(state(storedCard("a")), listOf(RemoteChange.Card(manual)))
        val merged = result.state.cards.single { it.id == "m" }
        assertEquals(null, merged.form.photo)
        assertTrue(merged.form.isCodeTyped)
        assertFalse(merged.addedToWallet)
        assertEquals(setOf("m"), result.accepted)
    }

    @Test
    fun newerRemoteVersionReplacesTheCardInPlace() {
        val result = LibraryMerge.apply(
            state(storedCard("a"), storedCard("b", updatedAt = 5)),
            listOf(RemoteChange.Card(storedCard("b", updatedAt = 9, name = "Renamed"))),
        )
        assertEquals(listOf("a", "b"), result.state.cards.map { it.id })
        assertEquals("Renamed", result.state.cards[1].form.name)
        assertEquals(setOf("b"), result.accepted)
        assertEquals(listOf(5L), result.dropped.map { it.updatedAt })
    }

    @Test
    fun olderRemoteVersionIsIgnoredAndReportedAsNewerHere() {
        val result = LibraryMerge.apply(state(storedCard("a", updatedAt = 9)), listOf(RemoteChange.Card(storedCard("a", updatedAt = 5, name = "Old"))))
        assertEquals("a", result.state.cards.single().form.name)
        assertEquals(setOf("a"), result.newerHere)
        assertTrue(result.accepted.isEmpty())
    }

    @Test
    fun newRemoteCardsAreAddedAfterTheExistingOnesAndNotInThisWallet() {
        val result = LibraryMerge.apply(state(storedCard("a")), listOf(RemoteChange.Card(storedCard("b"))))
        assertEquals(listOf("a", "b"), result.state.cards.map { it.id })
        assertFalse(result.state.cards[1].addedToWallet)
        assertTrue(result.state.cards[0].addedToWallet)
    }

    @Test
    fun replacingKeepsWhetherThePassIsInThisWallet() {
        val result = LibraryMerge.apply(state(storedCard("a", updatedAt = 1)), listOf(RemoteChange.Card(storedCard("a", updatedAt = 2).copy(addedToWallet = false))))
        assertTrue(result.state.cards.single().addedToWallet)
    }

    @Test
    fun deletionRemovesOlderCardsAndLeavesATombstone() {
        val result = LibraryMerge.apply(state(storedCard("a", updatedAt = 3)), listOf(RemoteChange.Deleted("a", deletedAt = 4)))
        assertTrue(result.state.cards.isEmpty())
        assertEquals(mapOf("a" to 4L), result.state.sync.tombstones)
        assertEquals(listOf("a"), result.dropped.map { it.id })
    }

    @Test
    fun deletionOlderThanALocalEditKeepsTheCard() {
        val result = LibraryMerge.apply(state(storedCard("a", updatedAt = 5)), listOf(RemoteChange.Deleted("a", deletedAt = 4)))
        assertEquals(listOf("a"), result.state.cards.map { it.id })
        assertEquals(setOf("a"), result.newerHere)
    }

    @Test
    fun tombstoneStopsAStaleCopyFromBringingACardBack() {
        val result = LibraryMerge.apply(
            state(sync = SyncMetadata(tombstones = mapOf("a" to 7))),
            listOf(RemoteChange.Card(storedCard("a", updatedAt = 6))),
        )
        assertTrue(result.state.cards.isEmpty())
        assertEquals(setOf("a"), result.newerHere)
    }

    @Test
    fun cardEditedAfterItsDeletionComesBack() {
        val result = LibraryMerge.apply(
            state(sync = SyncMetadata(tombstones = mapOf("a" to 7))),
            listOf(RemoteChange.Card(storedCard("a", updatedAt = 8))),
        )
        assertEquals(listOf("a"), result.state.cards.map { it.id })
        assertEquals(emptyMap(), result.state.sync.tombstones)
    }

    @Test
    fun revivingRestoresADeletedCardStampedNewerThanItsDeletion() {
        val result = LibraryMerge.apply(
            state(sync = SyncMetadata(tombstones = mapOf("a" to 7))),
            listOf(RemoteChange.Card(storedCard("a", updatedAt = 6))),
            revive = true,
            now = 100,
        )
        assertEquals(100L, result.state.cards.single().updatedAt)
        assertEquals(emptyMap(), result.state.sync.tombstones)
    }

    @Test
    fun newerOrderIsAppliedWithUnknownCardsOnTop() {
        val result = LibraryMerge.apply(
            state(storedCard("new"), storedCard("a"), storedCard("b"), storedCard("c"), sync = SyncMetadata(orderUpdatedAt = 1)),
            listOf(RemoteChange.Order(listOf("c", "gone", "a", "b"), updatedAt = 2)),
        )
        assertEquals(listOf("new", "c", "a", "b"), result.state.cards.map { it.id })
        assertEquals(2L, result.state.sync.orderUpdatedAt)
        assertFalse(result.isOrderNewerHere)
    }

    @Test
    fun olderOrderIsIgnoredAndReported() {
        val result = LibraryMerge.apply(
            state(storedCard("a"), storedCard("b"), sync = SyncMetadata(orderUpdatedAt = 5)),
            listOf(RemoteChange.Order(listOf("b", "a"), updatedAt = 2)),
        )
        assertEquals(listOf("a", "b"), result.state.cards.map { it.id })
        assertTrue(result.isOrderNewerHere)
    }
}
