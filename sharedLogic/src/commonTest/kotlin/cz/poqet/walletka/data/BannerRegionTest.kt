package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
