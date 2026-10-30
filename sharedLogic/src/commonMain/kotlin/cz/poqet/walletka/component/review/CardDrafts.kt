package cz.poqet.walletka.component.review

import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.domain.CardDraft

/** The pass for this card, as the wallet service issues it under [walletId]. */
internal fun CardForm.toDraft(walletId: String) = CardDraft(
    walletId = walletId,
    name = name.trim(),
    code = selectedCode.copy(value = selectedCode.value.trim()),
    backgroundColor = selectedColor,
    banner = banner.takeIf { isBannerEnabled },
    logo = logo.activeImage,
    details = details.trimmed(),
)
