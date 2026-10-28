package cz.poqet.walletka.domain


/** Rectangle in image coordinates normalized to `0..1`, origin at the top-left corner. */
data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun intersects(other: NormalizedRect): Boolean =
        left < other.right && other.left < right && top < other.bottom && other.top < bottom

    companion object {
        val Full = NormalizedRect(0f, 0f, 1f, 1f)
    }
}
