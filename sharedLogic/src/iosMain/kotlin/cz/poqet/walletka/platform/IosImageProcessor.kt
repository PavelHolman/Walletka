package cz.poqet.walletka.platform

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScanVariant
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
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreImage.CIContext
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.CoreImage.kCIInputContrastKey
import platform.CoreImage.kCIInputImageKey
import platform.CoreImage.kCIInputSaturationKey
import platform.Foundation.NSNumber
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIRectFill
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

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

    override suspend fun renderForScan(image: StoredImage, variant: ScanVariant): Either<AnalysisError, ByteArray> = background {
        val region = variant.region
        // Decode a crop at a higher resolution, so upscaling it adds real detail where the photo has it.
        val longerFraction = max(region.width, region.height).coerceAtLeast(0.05f)
        val sourceSize = (variant.maxSize / longerFraction).roundToInt().coerceAtMost(SCAN_SOURCE_MAX_SIZE)
        val cgImage = ensureNotNull(loadUpright(image.path, sourceSize)?.CGImage) { unreadable() }
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()
        val rect = CGRectMake(region.left * width, region.top * height, region.width * width, region.height * height)
        val cropped = ensureNotNull(CGImageCreateWithImageInRect(cgImage, rect)) { unreadable() }
        val croppedImage = UIImage(cGImage = cropped)
        val cropWidth = CGImageGetWidth(cropped).toDouble()
        val cropHeight = CGImageGetHeight(cropped).toDouble()
        CGImageRelease(cropped)

        val scale = variant.maxSize / max(cropWidth, cropHeight)
        val scaledWidth = cropWidth * scale
        val scaledHeight = cropHeight * scale
        val angle = variant.rotationDegrees * PI / 180
        val outWidth = (abs(scaledWidth * cos(angle)) + abs(scaledHeight * sin(angle))).roundToInt().coerceAtLeast(1).toDouble()
        val outHeight = (abs(scaledWidth * sin(angle)) + abs(scaledHeight * cos(angle))).roundToInt().coerceAtLeast(1).toDouble()
        val format = UIGraphicsImageRendererFormat().apply {
            this.scale = 1.0
            opaque = true
        }
        val rendered = UIGraphicsImageRenderer(size = CGSizeMake(outWidth, outHeight), format = format).imageWithActions { context ->
            UIColor.whiteColor.setFill()
            UIRectFill(CGRectMake(0.0, 0.0, outWidth, outHeight))
            val cg = context?.CGContext
            // UIKit's y axis points down, so a positive angle turns clockwise on screen.
            CGContextTranslateCTM(cg, outWidth / 2, outHeight / 2)
            CGContextRotateCTM(cg, angle)
            croppedImage.drawInRect(CGRectMake(-scaledWidth / 2, -scaledHeight / 2, scaledWidth, scaledHeight))
        }
        val output = if (variant.enhanced) ensureNotNull(enhance(rendered)) { unreadable() } else rendered
        ensureNotNull(UIImageJPEGRepresentation(output, 0.92)) { unreadable() }.toByteArray()
    }

    /** Greyscale with the contrast boosted around mid-grey. */
    private fun enhance(image: UIImage): UIImage? {
        val input = image.CGImage?.let { CIImage(cGImage = it) } ?: return null
        val filter = CIFilter.filterWithName(
            "CIColorControls",
            withInputParameters = mapOf<Any?, Any?>(
                kCIInputImageKey to input,
                kCIInputSaturationKey to NSNumber(double = 0.0),
                kCIInputContrastKey to NSNumber(double = 1.6),
            ),
        ) ?: return null
        val result = filter.outputImage ?: return null
        val cgImage = CIContext.contextWithOptions(null).createCGImage(result, fromRect = result.extent) ?: return null
        return UIImage(cGImage = cgImage).also { CGImageRelease(cgImage) }
    }

    private fun unreadable() = AnalysisError(AnalysisFailure.IMAGE_UNREADABLE)

    private suspend fun <T> background(block: arrow.core.raise.Raise<AnalysisError>.() -> T): Either<AnalysisError, T> =
        withContext(Dispatchers.Default) { either { block() } }
}
