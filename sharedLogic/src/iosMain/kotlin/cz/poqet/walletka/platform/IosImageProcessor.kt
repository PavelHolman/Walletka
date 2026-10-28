package cz.poqet.walletka.platform

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.StoredImage
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import kotlin.math.roundToInt

internal class IosImageProcessor : ImageProcessor {

    override suspend fun pixels(image: StoredImage, maxSize: Int): Either<AnalysisError, ImagePixels> = background {
        val cgImage = ensureNotNull(loadUpright(image.path, maxSize)?.CGImage) { unreadable() }
        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        val rgba = ByteArray(width * height * 4)
        rgba.usePinned { pinned ->
            val colorSpace = CGColorSpaceCreateDeviceRGB()
            val context = CGBitmapContextCreate(
                data = pinned.addressOf(0),
                width = width.toULong(),
                height = height.toULong(),
                bitsPerComponent = 8u,
                bytesPerRow = (width * 4).toULong(),
                space = colorSpace,
                bitmapInfo = CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value,
            )
            CGContextDrawImage(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()), cgImage)
            CGContextRelease(context)
            CGColorSpaceRelease(colorSpace)
        }
        val argb = IntArray(width * height) { i ->
            val r = rgba[i * 4].toInt() and 0xFF
            val g = rgba[i * 4 + 1].toInt() and 0xFF
            val b = rgba[i * 4 + 2].toInt() and 0xFF
            (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        ImagePixels(width, height, argb)
    }

    override suspend fun cropToPng(
        image: StoredImage,
        region: NormalizedRect,
        targetWidth: Int,
    ): Either<AnalysisError, ByteArray> = background {
        val cgImage = ensureNotNull(loadUpright(image.path, DETECTION_MAX_SIZE)?.CGImage) { unreadable() }
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()
        val rect = CGRectMake(region.left * width, region.top * height, region.width * width, region.height * height)
        val cropped = ensureNotNull(CGImageCreateWithImageInRect(cgImage, rect)) { unreadable() }
        val croppedImage = UIImage(cGImage = cropped)
        CGImageRelease(cropped)
        val targetHeight = (targetWidth * region.height * height / (region.width * width)).roundToInt().coerceAtLeast(1)
        val png = render(croppedImage, targetWidth.toDouble(), targetHeight.toDouble())
        ensureNotNull(UIImagePNGRepresentation(png)) { unreadable() }.toByteArray()
    }

    private fun unreadable() = AnalysisError(AnalysisFailure.IMAGE_UNREADABLE)

    private suspend fun <T> background(block: arrow.core.raise.Raise<AnalysisError>.() -> T): Either<AnalysisError, T> =
        withContext(Dispatchers.Default) { either { block() } }
}
