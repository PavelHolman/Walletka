package cz.poqet.walletka.data

import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedPoint
import cz.poqet.walletka.domain.NormalizedQuad
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CardFramingTest {

    private fun quad(left: Float, top: Float, right: Float, bottom: Float) = NormalizedQuad(
        NormalizedPoint(left, top), NormalizedPoint(right, top), NormalizedPoint(right, bottom), NormalizedPoint(left, bottom),
    )

    private val qr = ScannedCode(CodeFormat.QR, "x", bounds = NormalizedRect(0.3f, 0.45f, 0.7f, 0.75f))

    @Test
    fun cardOnATableIsTheCard() {
        assertTrue(CardFraming.isCard(quad(0.1f, 0.15f, 0.9f, 0.85f), photoAspectRatio = 0.75f, codes = listOf(qr)))
    }

    @Test
    fun theCodesWhiteBoxIsNotTheCard() {
        assertFalse(CardFraming.isCard(quad(0.28f, 0.43f, 0.72f, 0.77f), photoAspectRatio = 0.75f, codes = listOf(qr)))
    }

    @Test
    fun anOutlineThatCutsThroughTheCodeIsNotTheCard() {
        assertFalse(CardFraming.isCard(quad(0.1f, 0.1f, 0.9f, 0.6f), photoAspectRatio = 0.75f, codes = listOf(qr)))
    }

    @Test
    fun aPhotoThatIsAlreadyTheCardStaysAsItIs() {
        assertFalse(CardFraming.isCard(quad(0.01f, 0.01f, 0.99f, 0.99f), photoAspectRatio = 1.58f, codes = emptyList()))
    }

    @Test
    fun smallDetailsAndStripsAreNotCards() {
        assertFalse(CardFraming.isCard(quad(0.4f, 0.4f, 0.6f, 0.6f), photoAspectRatio = 1f, codes = emptyList()))
        assertFalse(CardFraming.isCard(quad(0.05f, 0.4f, 0.95f, 0.6f), photoAspectRatio = 1f, codes = emptyList()))
    }

    @Test
    fun tiltedCardIsAccepted() {
        val tilted = NormalizedQuad(
            NormalizedPoint(0.2f, 0.1f), NormalizedPoint(0.9f, 0.2f), NormalizedPoint(0.8f, 0.9f), NormalizedPoint(0.1f, 0.8f),
        )
        assertTrue(CardFraming.isCard(tilted, photoAspectRatio = 1f, codes = listOf(qr)))
    }

    @Test
    fun straightenedSizeFollowsTheSides() {
        assertEquals(800 to 600, CardFraming.straightenedSize(quad(0.1f, 0.1f, 0.9f, 0.7f), width = 1000, height = 1000))
    }
}
