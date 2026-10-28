package cz.poqet.walletka.domain


/** Everything needed to issue a wallet pass. */
internal data class CardDraft(
    val name: String,
    val code: ScannedCode,
    val backgroundColor: CardColor,
    /** PNG cut out of the original photo, shown as the strip (Apple) / hero (Google) image. */
    val banner: StoredImage?,
) {
    val foregroundColor: CardColor get() = backgroundColor.contentColor
}
