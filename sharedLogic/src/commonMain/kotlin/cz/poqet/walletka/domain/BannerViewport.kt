package cz.poqet.walletka.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Geometry of the photo crop editors (banner and logo), shared by the SwiftUI and Compose UIs.
 *
 * The editor shows a fixed window with the crop's aspect ratio ([aspectRatio], the wallet's strip by default) over
 * the photo, which the user pans and zooms. Lengths are in units of the window width: the window is `1` wide and
 * `1 / aspectRatio` tall, and the UI multiplies by its width in points/pixels. At zoom `1` the photo exactly covers
 * the window.
 *
 * @param photoAspectRatio width / height of the photo.
 * @param aspectRatio width / height of the crop.
 */
class BannerViewport(val photoAspectRatio: Float, val aspectRatio: Float) {

    /** The banner (strip) editor. */
    constructor(photoAspectRatio: Float) : this(photoAspectRatio, ASPECT_RATIO)

    /** Photo placement: its size and top-left corner relative to the window's top-left corner. */
    data class Transform(val zoom: Float, val width: Float, val height: Float, val offsetX: Float, val offsetY: Float)

    val windowHeight: Float get() = 1f / aspectRatio

    /** Photo height at zoom 1: the smallest size that still covers the window. */
    private val coverHeight: Float get() = max(windowHeight, 1f / photoAspectRatio)

    /** Where the photo must be drawn so that the window shows [region]. */
    fun transformFor(region: NormalizedRect): Transform {
        val clamped = clamp(region)
        val width = 1f / clamped.width
        val height = width / photoAspectRatio
        return Transform(height / coverHeight, width, height, -clamped.left * width, -clamped.top * height)
    }

    /**
     * The region the window shows for a photo at [zoom] whose top-left corner is at ([offsetX], [offsetY]).
     * Out-of-range values are clamped so the photo always covers the window.
     */
    fun regionFor(zoom: Float, offsetX: Float, offsetY: Float): NormalizedRect {
        val height = coverHeight * zoom.coerceIn(1f, MAX_ZOOM)
        val width = height * photoAspectRatio
        val x = offsetX.coerceIn(1f - width, 0f)
        val y = offsetY.coerceIn(windowHeight - height, 0f)
        return NormalizedRect(-x / width, -y / height, (1f - x) / width, (windowHeight - y) / height)
    }

    /**
     * Applies a pinch: scales by [scaleBy] around the point ([anchorX], [anchorY]) of the window, then pans by
     * ([panX], [panY]). Returns the resulting region.
     */
    fun applyGesture(
        region: NormalizedRect,
        scaleBy: Float,
        anchorX: Float,
        anchorY: Float,
        panX: Float,
        panY: Float,
    ): NormalizedRect {
        val current = transformFor(region)
        val zoom = (current.zoom * scaleBy).coerceIn(1f, MAX_ZOOM)
        val factor = zoom / current.zoom
        val offsetX = anchorX - (anchorX - current.offsetX) * factor + panX
        val offsetY = anchorY - (anchorY - current.offsetY) * factor + panY
        return regionFor(zoom, offsetX, offsetY)
    }

    /** Forces [region] to the crop's aspect ratio and inside the photo, keeping its centre where possible. */
    fun clamp(region: NormalizedRect): NormalizedRect {
        // A region `w` wide (normalized) is `w * photoAspectRatio / aspectRatio` tall.
        val heightPerWidth = photoAspectRatio / aspectRatio
        val maxWidth = min(1f, 1f / heightPerWidth)
        val width = region.width.takeIf { it > 0f }?.coerceIn(maxWidth / MAX_ZOOM, maxWidth) ?: maxWidth
        val height = width * heightPerWidth
        val centerX = (region.left + region.right) / 2
        val centerY = (region.top + region.bottom) / 2
        val left = (centerX - width / 2).coerceIn(0f, 1f - width)
        val top = (centerY - height / 2).coerceIn(0f, 1f - height)
        return NormalizedRect(left, top, left + width, top + height)
    }

    companion object {
        /** Apple Wallet store-card strip image is 375x123 points. */
        const val ASPECT_RATIO = 375f / 123f

        const val MAX_ZOOM = 6f
    }
}
