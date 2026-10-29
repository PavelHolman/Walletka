package cz.poqet.walletka.data

import cz.poqet.walletka.domain.BannerViewport
import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.TextLine
import cz.poqet.walletka.platform.WalletPlatform
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CardDetailsTest {

    @Test
    fun datesRoundTrip() {
        assertEquals("1970-01-01", CardDetails.formatDate(0))
        assertEquals("2000-02-29", CardDetails.formatDate(11_016))
        assertEquals(20_969L, CardDetails.parseDate("2027-05-31"))
        for (day in listOf(-1L, 0L, 59L, 11_016L, 20_969L, 2_932_896L)) {
            assertEquals(day, CardDetails.parseDate(CardDetails.formatDate(day)))
        }
    }

    @Test
    fun invalidDatesAreRejected() {
        assertNull(CardDetails.parseDate("2027-02-29"))
        assertNull(CardDetails.parseDate("2027-13-01"))
        assertNull(CardDetails.parseDate("31.5.2027"))
        assertNull(CardDetails.parseDate(""))
    }

    @Test
    fun trimmedDropsWhitespaceAndInvalidExpiry() {
        val details = CardDetails(memberNumber = " 42 ", memberName = "", expiryDate = "soon", notes = "  ")
        assertEquals(CardDetails.Empty.copy(memberNumber = "42"), details.trimmed())
        assertTrue(CardDetails.Empty.isEmpty)
    }

    @Test
    fun logoFramesTheBrandNameInTheWalletsShape() {
        val code = ScannedCode(CodeFormat.QR, "x", NormalizedRect(0.3f, 0.6f, 0.7f, 0.9f))
        val name = TextLine("COFFEE CLUB", NormalizedRect(0.3f, 0.1f, 0.7f, 0.16f))
        for (platform in WalletPlatform.entries) {
            val viewport = LogoRegion.viewport(photoAspectRatio = 0.75f, platform)
            val region = LogoRegion.pick(viewport, listOf(code), listOf(name))
            assertEquals(LogoRegion.aspectRatio(platform), region.width * 0.75f / region.height, 0.01f)
            assertTrue(region.left <= 0.3f && region.right >= 0.7f && region.top <= 0.1f && region.bottom >= 0.16f, "$region")
            assertTrue(!region.intersects(code.bounds!!), "$region")
        }
    }

    @Test
    fun logoWithoutTextSitsAtTheTop() {
        val viewport = BannerViewport(photoAspectRatio = 1.5f, aspectRatio = 1f)
        val region = LogoRegion.pick(viewport, emptyList(), emptyList())
        assertEquals(0f, region.top)
        assertEquals(1f, region.bottom, 0.001f)
    }
}
