package cz.poqet.walletka.domain

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * A processed copy of a photo for another barcode detection attempt, see
 * [cz.poqet.walletka.platform.ImageProcessor.renderForScan]. Applied in order: crop to [region] (scaled so its
 * longer side is [maxSize] pixels, decoded from the photo at a higher resolution where it has one), rotate by
 * [rotationDegrees], then [enhanced].
 */
internal data class ScanVariant(
    /** Part of the upright photo to keep. */
    val region: NormalizedRect = NormalizedRect.Full,
    /**
     * Clockwise rotation as seen on screen. The canvas grows to fit the whole rotated image; the corners are
     * filled white (a quiet zone for codes).
     */
    val rotationDegrees: Float = 0f,
    /** Greyscale with boosted contrast, which helps with glare, faded print and coloured codes. */
    val enhanced: Boolean = false,
    val maxSize: Int = 2048,
) {
    /**
     * Maps [rect], normalized in this variant's image, back to the photo it was rendered from, whose width / height
     * is [photoAspectRatio].
     */
    fun toPhoto(rect: NormalizedRect, photoAspectRatio: Float): NormalizedRect {
        val cropAspect = region.width * photoAspectRatio / region.height
        val corners = listOf(rect.left to rect.top, rect.right to rect.top, rect.left to rect.bottom, rect.right to rect.bottom)
            .map { (x, y) -> unrotate(x, y, cropAspect) }
            .map { (x, y) -> region.left + x * region.width to region.top + y * region.height }
        return NormalizedRect(
            left = corners.minOf { it.first }.coerceIn(0f, 1f),
            top = corners.minOf { it.second }.coerceIn(0f, 1f),
            right = corners.maxOf { it.first }.coerceIn(0f, 1f),
            bottom = corners.maxOf { it.second }.coerceIn(0f, 1f),
        )
    }

    /** A normalized point of the rotated canvas → the same point normalized in the unrotated image ([aspect] wide, 1 tall). */
    private fun unrotate(x: Float, y: Float, aspect: Float): Pair<Float, Float> {
        if (rotationDegrees == 0f) return x to y
        val angle = rotationDegrees * PI / 180
        val c = cos(angle)
        val s = sin(angle)
        val canvasWidth = abs(aspect * c) + abs(s)
        val canvasHeight = abs(aspect * s) + abs(c)
        val dx = x * canvasWidth - canvasWidth / 2
        val dy = y * canvasHeight - canvasHeight / 2
        // Inverse of the clockwise rotation in y-down coordinates.
        val ox = dx * c + dy * s
        val oy = -dx * s + dy * c
        return ((ox + aspect / 2) / aspect).toFloat() to (oy + 0.5).toFloat()
    }

    companion object {
        /** Side length of a [tiles] cell, so neighbouring cells overlap and a code on a seam is whole in one. */
        private const val TILE_SIZE = 0.6f

        /** Overlapping quadrants of the photo, each upscaled, for small or dense codes. */
        fun tiles(): List<NormalizedRect> = listOf(0f, 1f - TILE_SIZE).flatMap { top ->
            listOf(0f, 1f - TILE_SIZE).map { left -> NormalizedRect(left, top, left + TILE_SIZE, top + TILE_SIZE) }
        }

        val Centre = NormalizedRect(0.2f, 0.2f, 0.8f, 0.8f)
    }
}
