package cz.poqet.walletka.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LogoBackgroundTest {

    /** A [size]² brown image with a white square in the middle. */
    private fun logo(size: Int = 20): ByteArray = ByteArray(size * size * 4) { i ->
        val pixel = i / 4
        val x = pixel % size
        val y = pixel / size
        val inside = x in 6..13 && y in 6..13
        when (i % 4) {
            3 -> 0xFF.toByte()
            0 -> (if (inside) 0xFF else 0x6B).toByte()
            1 -> (if (inside) 0xFF else 0x3A).toByte()
            else -> (if (inside) 0xFF else 0x20).toByte()
        }
    }

    @Test
    fun plainBackgroundBecomesTransparentAndTheLogoStays() {
        val rgba = logo()
        assertTrue(LogoBackground.cutOut(rgba, 20, 20))
        assertEquals(0, rgba[3].toInt() and 0xFF) // corner: background
        val centre = (10 * 20 + 10) * 4
        assertEquals(0xFF, rgba[centre + 3].toInt() and 0xFF)
        assertEquals(0xFF, rgba[centre].toInt() and 0xFF)
    }

    @Test
    fun busyBordersAreLeftAlone() {
        val noise = ByteArray(20 * 20 * 4) { i -> if (i % 4 == 3) 0xFF.toByte() else ((i * 37) % 256).toByte() }
        val copy = noise.copyOf()
        assertFalse(LogoBackground.cutOut(noise, 20, 20))
        assertTrue(noise.contentEquals(copy))
    }
}
