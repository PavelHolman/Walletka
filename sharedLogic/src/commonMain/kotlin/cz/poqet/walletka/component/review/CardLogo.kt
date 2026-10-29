package cz.poqet.walletka.component.review

import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.StoredImage

/** The brand logo cut out of the card photo; off unless the user turns it on. */
data class CardLogo(
    /** PNG cut out of the photo; `null` until the logo is first turned on, or when cutting it failed. */
    val image: StoredImage?,
    val isEnabled: Boolean,
    /** Part of the photo the logo is cut from; see [CardReviewComponent.CardForm.logoViewport]. */
    val region: NormalizedRect,
) {
    /** The image the pass shows, if any. */
    val activeImage: StoredImage? get() = image?.takeIf { isEnabled }
}
