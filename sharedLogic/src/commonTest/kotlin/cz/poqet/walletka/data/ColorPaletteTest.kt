package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.ImagePixels
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ColorPaletteTest {

    @Test
    fun saturatedBrandColourBeatsLargerGreyArea() {
        val white = 0xFFF0F0F0.toInt()
        val red = 0xFFD32F2F.toInt()
        val pixels = IntArray(100) { if (it < 35) red else white }
        val palette = ColorPalette.extract(ImagePixels(10, 10, pixels))
        val first = palette.first()
        assertTrue(first.red > 180 && first.green < 80, "expected red first, got $palette")
        assertEquals(2, palette.size)
    }

    @Test
    fun contentColourHasContrast() {
        assertEquals(CardColor.Black, CardColor(255, 235, 59).contentColor)
        assertEquals(CardColor.White, CardColor(26, 35, 126).contentColor)
        assertEquals("#1a237e", CardColor(26, 35, 126).hex)
    }
}
