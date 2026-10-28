package cz.poqet.walletka.data

import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode

/**
 * Chooses which part of the photo becomes the card banner: a full-width band with the wallet's strip aspect
 * ratio that does not cover the code (the code is rendered by the wallet itself).
 */
internal object BannerRegion {

    /** Apple Wallet store-card strip image is 375x123 points. */
    const val ASPECT_RATIO = 375f / 123f

    /**
     * @param imageAspectRatio width / height of the source image.
     */
    fun pick(imageAspectRatio: Float, codes: List<ScannedCode>): NormalizedRect {
        val bandHeight = (imageAspectRatio / ASPECT_RATIO).coerceAtMost(1f)
        val candidates = generateSequence(0f) { it + 0.05f }
            .takeWhile { it + bandHeight <= 1.0001f }
            .map { top -> NormalizedRect(0f, top, 1f, (top + bandHeight).coerceAtMost(1f)) }
            .toList()
        val codeBounds = codes.mapNotNull { it.bounds }
        return candidates.firstOrNull { band -> codeBounds.none { it.intersects(band) } }
            ?: candidates.minBy { band -> codeBounds.sumOf { overlap(it, band).toDouble() } }
    }

    private fun overlap(a: NormalizedRect, b: NormalizedRect): Float {
        val w = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
        val h = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        return w * h
    }
}
