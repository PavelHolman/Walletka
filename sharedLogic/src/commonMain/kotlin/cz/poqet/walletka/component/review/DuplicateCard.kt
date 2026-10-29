package cz.poqet.walletka.component.review

import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.data.StoredCard
import cz.poqet.walletka.domain.ScannedCode

/** A library card with the same code as the new card being reviewed: probably the same card, added before. */
data class DuplicateCard(val cardId: String, val name: String)

/** The library card whose code (format and value) is [code]'s, if any. */
internal fun List<StoredCard>.findDuplicate(code: ScannedCode): DuplicateCard? {
    val value = code.value.trim()
    if (value.isEmpty()) return null
    return firstOrNull { card ->
        val stored = card.form.codes.getOrNull(card.form.selectedCodeIndex) ?: return@firstOrNull false
        stored.format == code.format && stored.value.trim() == value
    }?.let { DuplicateCard(it.id, it.form.name) }
}

/** Sets [Model.Editing.duplicateOf] for a new card (library cards are never duplicates of themselves). */
internal fun Model.withDuplicateOf(cards: List<StoredCard>): Model {
    if (this !is Model.Editing) return this
    val duplicate = if (form.isLibraryCard) null else cards.findDuplicate(form.selectedCode)
    return if (duplicate == duplicateOf) this else copy(duplicateOf = duplicate)
}
