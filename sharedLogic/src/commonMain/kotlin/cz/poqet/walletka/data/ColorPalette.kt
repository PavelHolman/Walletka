package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.ImagePixels
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Picks card background colours from a photo.
 *
 * Pixels are quantized into a 4-bit-per-channel histogram; buckets are ranked by population weighted with
 * saturation, so a brand colour wins over large neutral areas (paper, table, background).
 */
internal object ColorPalette {

    fun extract(pixels: ImagePixels, maxColors: Int = 5): List<CardColor> {
        val counts = HashMap<Int, Bucket>()
        for (argb in pixels.argb) {
            if ((argb ushr 24) < 128) continue
            val key = ((argb shr 20) and 0xF shl 8) or ((argb shr 12) and 0xF shl 4) or ((argb shr 4) and 0xF)
            counts.getOrPut(key) { Bucket() }.add(argb)
        }
        val ranked = counts.values
            .map { it.average() to it.count * (0.15 + saturation(it.average())) }
            .sortedByDescending { it.second }
            .map { it.first }

        val result = mutableListOf<CardColor>()
        for (color in ranked) {
            if (result.none { distance(it, color) < 60 }) result += color
            if (result.size == maxColors) break
        }
        return result
    }

    /** Average colour of the pixels within [radius] of the normalized point ([x], [y]). */
    fun sample(pixels: ImagePixels, x: Float, y: Float, radius: Int = 2): CardColor {
        val cx = (x.coerceIn(0f, 1f) * (pixels.width - 1)).toInt()
        val cy = (y.coerceIn(0f, 1f) * (pixels.height - 1)).toInt()
        val bucket = Bucket()
        for (py in (cy - radius)..(cy + radius)) {
            for (px in (cx - radius)..(cx + radius)) {
                if (px in 0 until pixels.width && py in 0 until pixels.height) bucket.add(pixels.argb[py * pixels.width + px])
            }
        }
        return bucket.average()
    }

    internal fun saturation(c: CardColor): Double {
        val hi = max(c.red, max(c.green, c.blue))
        val lo = min(c.red, min(c.green, c.blue))
        return if (hi == 0) 0.0 else (hi - lo).toDouble() / hi
    }

    private fun distance(a: CardColor, b: CardColor): Int =
        abs(a.red - b.red) + abs(a.green - b.green) + abs(a.blue - b.blue)

    private class Bucket {
        var count = 0
        private var r = 0L
        private var g = 0L
        private var b = 0L

        fun add(argb: Int) {
            count++
            r += (argb shr 16) and 0xFF
            g += (argb shr 8) and 0xFF
            b += argb and 0xFF
        }

        fun average() = CardColor((r / count).toInt(), (g / count).toInt(), (b / count).toInt())
    }
}
