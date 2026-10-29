package cz.poqet.walletka.data

import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScanVariant
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class ScanVariantTest {

    private fun assertClose(expected: NormalizedRect, actual: NormalizedRect) {
        val close = listOf(
            expected.left to actual.left,
            expected.top to actual.top,
            expected.right to actual.right,
            expected.bottom to actual.bottom,
        ).all { (a, b) -> abs(a - b) < 1e-4f }
        assertTrue(close, "expected $expected, got $actual")
    }

    @Test
    fun plainVariantKeepsBounds() {
        val rect = NormalizedRect(0.1f, 0.2f, 0.3f, 0.4f)
        assertClose(rect, ScanVariant().toPhoto(rect, photoAspectRatio = 0.75f))
    }

    @Test
    fun cropBoundsAreMappedIntoThePhoto() {
        val variant = ScanVariant(region = NormalizedRect(0.4f, 0.4f, 1f, 1f))
        assertClose(NormalizedRect(0.4f, 0.55f, 0.7f, 1f), variant.toPhoto(NormalizedRect(0f, 0.25f, 0.5f, 1f), photoAspectRatio = 1.5f))
    }

    @Test
    fun quarterTurnIsUndone() {
        // A 2:1 photo turned clockwise is 1:2 tall; the photo's top-left quarter ends up top-right.
        val variant = ScanVariant(rotationDegrees = 90f)
        assertClose(NormalizedRect(0f, 0f, 0.5f, 0.5f), variant.toPhoto(NormalizedRect(0.5f, 0f, 1f, 0.5f), photoAspectRatio = 2f))
    }

    @Test
    fun smallRotationKeepsTheCodeCentred() {
        val mapped = ScanVariant(rotationDegrees = 20f).toPhoto(NormalizedRect(0.45f, 0.45f, 0.55f, 0.55f), photoAspectRatio = 1f)
        assertTrue(abs((mapped.left + mapped.right) / 2 - 0.5f) < 1e-4f && abs((mapped.top + mapped.bottom) / 2 - 0.5f) < 1e-4f, "$mapped")
        // The box grows a little to bound the turned square.
        assertTrue(mapped.width > 0.1f && mapped.width < 0.2f, "$mapped")
    }
}
