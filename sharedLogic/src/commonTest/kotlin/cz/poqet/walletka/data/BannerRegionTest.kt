package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BannerRegionTest {

    @Test
    fun avoidsTheCode() {
        // Portrait photo with the code at the top.
        val code = ScannedCode(CodeFormat.QR, "x", NormalizedRect(0.3f, 0.05f, 0.7f, 0.3f))
        val band = BannerRegion.pick(imageAspectRatio = 0.75f, codes = listOf(code))
        assertFalse(band.intersects(code.bounds!!))
        assertEquals(0.75f / BannerRegion.ASPECT_RATIO, band.height, 0.001f)
    }

    @Test
    fun wideImageUsesFullHeight() {
        val band = BannerRegion.pick(imageAspectRatio = 4f, codes = emptyList())
        assertEquals(NormalizedRect(0f, 0f, 1f, 1f), band)
    }

    @Test
    fun framesTheBrandName() {
        val code = ScannedCode(CodeFormat.QR, "x", NormalizedRect(0.25f, 0.45f, 0.75f, 0.8f))
        val title = TextLine("COFFEE CLUB", NormalizedRect(0.15f, 0.2f, 0.75f, 0.26f))
        val band = BannerRegion.pick(imageAspectRatio = 0.75f, codes = listOf(code), lines = listOf(title))
        assertTrue(band.left <= title.bounds.left && band.right >= title.bounds.right, "$band")
        assertTrue(band.top <= title.bounds.top && band.bottom >= title.bounds.bottom, "$band")
        assertFalse(band.intersects(code.bounds!!))
        assertTrue(band.width < 1f, "should crop tighter than the full width: $band")
    }
}
