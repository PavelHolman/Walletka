package cz.poqet.walletka.domain


/** A machine-readable code found in a photo. */
data class ScannedCode(
    val format: CodeFormat,
    val value: String,
    val bounds: NormalizedRect? = null,
)

/**
 * A line of text recognized in a photo, used to suggest the card name.
 *
 * [bounds] carries the text height that ranks lines by visual prominence; [confidence] is the recognizer's
 * certainty in `0..1` (1 when the platform does not report one).
 */
data class TextLine(
    val text: String,
    val bounds: NormalizedRect,
    val confidence: Float = 1f,
)
