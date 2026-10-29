package cz.poqet.walletka.data

import cz.poqet.walletka.domain.BannerViewport
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine

/**
 * Chooses which part of the photo becomes the card banner by default: a crop with the wallet's strip aspect ratio
 * framing the most prominent text (usually the brand name), never covering the code, which the wallet renders
 * itself. The user can adjust it afterwards.
 */
internal object BannerRegion {

    const val ASPECT_RATIO = BannerViewport.ASPECT_RATIO

    /** Horizontal margin around the framed text, relative to its width. */
    private const val TEXT_MARGIN = 1.3f

    /** The band is at least this many times as tall as the framed text line. */
    private const val MIN_TEXT_HEIGHT_RATIO = 2.4f

    /**
     * @param imageAspectRatio width / height of the source image.
     */
    fun pick(imageAspectRatio: Float, codes: List<ScannedCode>, lines: List<TextLine> = emptyList()): NormalizedRect {
        val codeBounds = codes.mapNotNull { it.bounds }
        framingText(imageAspectRatio, codeBounds, lines)?.let { return it }

        val bandHeight = (imageAspectRatio / ASPECT_RATIO).coerceAtMost(1f)
        val candidates = generateSequence(0f) { it + 0.05f }
            .takeWhile { it + bandHeight <= 1.0001f }
            .map { top -> NormalizedRect(0f, top, 1f, (top + bandHeight).coerceAtMost(1f)) }
            .toList()
        return candidates.firstOrNull { band -> codeBounds.none { it.intersects(band) } }
            ?: candidates.minBy { band -> codeBounds.sumOf { overlap(it, band).toDouble() } }
    }

    private fun framingText(imageAspectRatio: Float, codeBounds: List<NormalizedRect>, lines: List<TextLine>): NormalizedRect? {
        val viewport = BannerViewport(imageAspectRatio)
        return lines
            .filter { line -> line.text.count { it.isLetter() } >= 2 && codeBounds.none { it.intersects(line.bounds) } }
            .sortedByDescending { it.bounds.height }
            .firstNotNullOfOrNull { line ->
                val width = maxOf(
                    line.bounds.width * TEXT_MARGIN,
                    line.bounds.height * MIN_TEXT_HEIGHT_RATIO * ASPECT_RATIO / imageAspectRatio,
                )
                val centerX = (line.bounds.left + line.bounds.right) / 2
                val centerY = (line.bounds.top + line.bounds.bottom) / 2
                val height = width * imageAspectRatio / ASPECT_RATIO
                val region = viewport.clamp(
                    NormalizedRect(centerX - width / 2, centerY - height / 2, centerX + width / 2, centerY + height / 2),
                )
                region.takeIf { band -> codeBounds.none { it.intersects(band) } }
            }
    }

    private fun overlap(a: NormalizedRect, b: NormalizedRect): Float {
        val w = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
        val h = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        return w * h
    }
}
