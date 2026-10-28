package cz.poqet.walletka.domain


/** A machine-readable code found in a photo. */
data class ScannedCode(
    val format: CodeFormat,
    val value: String,
    val bounds: NormalizedRect? = null,
)

/** A line of text recognized in a photo, used to suggest the card name. */
data class TextLine(
    val text: String,
    val bounds: NormalizedRect,
)
