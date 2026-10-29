package cz.poqet.walletka.data

import cz.poqet.walletka.domain.NormalizedPoint
import cz.poqet.walletka.domain.NormalizedQuad
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decides whether an outline found in a photo is the card itself, so the photo can be cut down to it: the banner,
 * logo and colours then come from the card rather than the table it lies on.
 */
internal object CardFraming {

    /** Smallest share of the photo the card must cover; smaller outlines are details on the card. */
    private const val MIN_AREA = 0.15f

    /** Above this share the photo already is just the card. */
    private const val MAX_AREA = 0.9f

    /** Shortest side / longest side; cards are rectangles, not strips. */
    private const val MIN_SIDE_RATIO = 0.4f

    /** A code may fill at most this share of the card; more means the outline is the code's own white box. */
    private const val MAX_CODE_SHARE = 0.5f

    /**
     * Whether [quad] (in a photo of [photoAspectRatio], width / height) frames the card: big enough, card-shaped,
     * and around every code found in the photo without being one of them.
     */
    fun isCard(quad: NormalizedQuad, photoAspectRatio: Float, codes: List<ScannedCode>): Boolean {
        val area = area(quad, photoAspectRatio) / photoAspectRatio
        if (area !in MIN_AREA..MAX_AREA) return false
        val sides = sides(quad, photoAspectRatio)
        val horizontal = (sides[0] + sides[2]) / 2
        val vertical = (sides[1] + sides[3]) / 2
        if (min(horizontal, vertical) / max(horizontal, vertical) < MIN_SIDE_RATIO) return false
        return codes.all { code ->
            val bounds = code.bounds ?: return@all true
            bounds.corners().all { contains(quad, it) } &&
                bounds.width * bounds.height / area < MAX_CODE_SHARE
        }
    }

    /**
     * [quad] pulled in by [fraction] of its size towards its centre: cards have rounded corners, so the outline's
     * corners show a bit of the background, which would end up in the banner and logo.
     */
    fun inset(quad: NormalizedQuad, fraction: Float = CORNER_INSET): NormalizedQuad {
        val cx = quad.corners.map { it.x }.average().toFloat()
        val cy = quad.corners.map { it.y }.average().toFloat()
        fun NormalizedPoint.pulled() = NormalizedPoint(x + (cx - x) * fraction * 2, y + (cy - y) * fraction * 2)
        return NormalizedQuad(quad.topLeft.pulled(), quad.topRight.pulled(), quad.bottomRight.pulled(), quad.bottomLeft.pulled())
    }

    /** Share of the card's size cut from each side by [inset]; about the radius of a card's corners. */
    private const val CORNER_INSET = 0.025f

    /** Pixel size of the straightened card for a photo of [width] × [height] pixels. */
    fun straightenedSize(quad: NormalizedQuad, width: Int, height: Int): Pair<Int, Int> {
        val sides = sides(quad, width.toFloat() / height)
        // Sides are in photo heights.
        return ((sides[0] + sides[2]) / 2 * height).roundToInt() to ((sides[1] + sides[3]) / 2 * height).roundToInt()
    }

    /** Side lengths top, right, bottom, left, in photo heights. */
    private fun sides(quad: NormalizedQuad, aspect: Float): List<Float> =
        quad.corners.zipWithNext().plus(quad.bottomLeft to quad.topLeft).map { (a, b) ->
            hypot((a.x - b.x) * aspect, a.y - b.y)
        }

    /** Shoelace area in photo heights², i.e. `aspect` for the whole photo. */
    private fun area(quad: NormalizedQuad, aspect: Float): Float {
        val corners = quad.corners
        var sum = 0f
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            sum += a.x * aspect * b.y - b.x * aspect * a.y
        }
        return abs(sum) / 2
    }

    /** Point in convex polygon: on the same side of every edge. */
    private fun contains(quad: NormalizedQuad, point: NormalizedPoint): Boolean {
        val corners = quad.corners
        var sign = 0
        for (i in corners.indices) {
            val a = corners[i]
            val b = corners[(i + 1) % corners.size]
            val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
            val side = if (cross > 0) 1 else if (cross < 0) -1 else 0
            if (side != 0) {
                if (sign != 0 && side != sign) return false
                sign = side
            }
        }
        return true
    }

    private fun NormalizedRect.corners() = listOf(
        NormalizedPoint(left, top),
        NormalizedPoint(right, top),
        NormalizedPoint(right, bottom),
        NormalizedPoint(left, bottom),
    )
}
