package cz.poqet.walletka.data

import cz.poqet.walletka.domain.BannerViewport
import cz.poqet.walletka.domain.NormalizedRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BannerViewportTest {

    private fun assertRect(expected: NormalizedRect, actual: NormalizedRect) {
        listOf(
            expected.left to actual.left, expected.top to actual.top,
            expected.right to actual.right, expected.bottom to actual.bottom,
        ).forEach { (e, a) -> assertEquals(e, a, 0.001f, "expected $expected, got $actual") }
    }

    @Test
    fun regionAndTransformRoundTrip() {
        val viewport = BannerViewport(photoAspectRatio = 0.75f)
        val region = viewport.clamp(NormalizedRect(0.2f, 0.3f, 0.7f, 0.4f))
        val t = viewport.transformFor(region)
        assertRect(region, viewport.regionFor(t.zoom, t.offsetX, t.offsetY))
    }

    @Test
    fun clampKeepsStripAspectInsidePhoto() {
        val viewport = BannerViewport(photoAspectRatio = 0.75f)
        val region = viewport.clamp(NormalizedRect(0.8f, 0.9f, 1.4f, 1.2f))
        val pixelAspect = region.width * 0.75f / region.height
        assertEquals(BannerViewport.ASPECT_RATIO, pixelAspect, 0.01f)
        assertTrue(region.left >= 0f && region.right <= 1.0001f && region.top >= 0f && region.bottom <= 1.0001f, "$region")
    }

    @Test
    fun zoomOneCoversWindowAndPanIsClamped() {
        val viewport = BannerViewport(photoAspectRatio = 0.75f)
        // Panning far right/down at zoom 1 stops at the photo's top-left corner.
        val region = viewport.regionFor(zoom = 1f, offsetX = 5f, offsetY = 5f)
        assertRect(NormalizedRect(0f, 0f, 1f, 0.75f / BannerViewport.ASPECT_RATIO), region)
    }

    @Test
    fun pinchZoomsAroundAnchor() {
        val viewport = BannerViewport(photoAspectRatio = 1.5f)
        val start = viewport.clamp(NormalizedRect(0.25f, 0.25f, 0.75f, 0.75f))
        val zoomed = viewport.applyGesture(start, scaleBy = 2f, anchorX = 0.5f, anchorY = viewport.windowHeight / 2, panX = 0f, panY = 0f)
        assertEquals(start.width / 2, zoomed.width, 0.001f)
        assertEquals((start.left + start.right) / 2, (zoomed.left + zoomed.right) / 2, 0.001f)
    }
}
