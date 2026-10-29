package cz.poqet.walletka.data

import kotlin.math.sqrt

/**
 * Removes the background of a logo cut out of a card: the colour along the crop's border becomes transparent, with a
 * soft edge so the letters stay smooth. Logos whose border isn't one colour (a photo, a pattern) are left alone.
 */
internal object LogoBackground {

    /** Colours this close to the background are fully transparent… */
    private const val TRANSPARENT_BELOW = 36.0

    /** …and from here on fully opaque; in between they fade, which keeps anti-aliased edges smooth. */
    private const val OPAQUE_FROM = 84.0

    /** Share of border pixels that must match the background colour for it to count as plain. */
    private const val PLAIN_BORDER = 0.7

    /**
     * Cuts the background out of [rgba] (8-bit RGBA rows of an opaque image, [width] × [height]) in place, writing
     * premultiplied alpha as Core Graphics and Android bitmaps expect it. Returns `false` when it left the image as is.
     */
    fun cutOut(rgba: ByteArray, width: Int, height: Int): Boolean {
        if (width < 4 || height < 4) return false
        val border = borderPixels(width, height)
        val background = IntArray(3) { channel -> border.map { rgba.channel(it, channel) }.sorted()[border.size / 2] }
        val matching = border.count { distance(rgba, it, background) < TRANSPARENT_BELOW }
        if (matching < border.size * PLAIN_BORDER) return false
        for (pixel in 0 until width * height) {
            val d = distance(rgba, pixel, background)
            val alpha = ((d - TRANSPARENT_BELOW) / (OPAQUE_FROM - TRANSPARENT_BELOW)).coerceIn(0.0, 1.0)
            val i = pixel * 4
            for (channel in 0..2) rgba[i + channel] = (rgba.channel(pixel, channel) * alpha).toInt().toByte()
            rgba[i + 3] = (alpha * 255).toInt().toByte()
        }
        return true
    }

    /** The outer two-pixel ring. */
    private fun borderPixels(width: Int, height: Int): List<Int> = buildList {
        for (y in 0 until height) for (x in 0 until width) {
            if (x < 2 || y < 2 || x >= width - 2 || y >= height - 2) add(y * width + x)
        }
    }

    private fun ByteArray.channel(pixel: Int, channel: Int) = this[pixel * 4 + channel].toInt() and 0xFF

    private fun distance(rgba: ByteArray, pixel: Int, color: IntArray): Double {
        var sum = 0.0
        for (channel in 0..2) {
            val d = (rgba.channel(pixel, channel) - color[channel]).toDouble()
            sum += d * d
        }
        return sqrt(sum)
    }
}
