package cz.poqet.walletka.domain


/** Everything needed to issue a wallet pass. */
internal data class CardDraft(
    /** Stable pass identity (Apple serial number), so re-issuing the card replaces the previous pass. */
    val walletId: String,
    val name: String,
    val code: ScannedCode,
    val backgroundColor: CardColor,
    /** PNG cut out of the original photo, shown as the strip (Apple) / hero (Google) image. */
    val banner: StoredImage?,
    /** PNG cut out of the photo, shown as the pass logo. */
    val logo: StoredImage? = null,
    val details: CardDetails = CardDetails.Empty,
) {
    val foregroundColor: CardColor get() = backgroundColor.contentColor
}
