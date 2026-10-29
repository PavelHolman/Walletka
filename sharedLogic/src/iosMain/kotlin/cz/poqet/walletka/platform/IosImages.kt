package cz.poqet.walletka.platform

import cz.poqet.walletka.domain.NormalizedRect
import kotlinx.cinterop.CValue
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import kotlin.math.max
import kotlin.math.min

/** Max resolution fed to Vision; plenty for codes, keeps memory bounded. */
internal const val DETECTION_MAX_SIZE = 2048

/** Max resolution a photo is decoded at for an upscaled crop in a scan retry. */
internal const val SCAN_SOURCE_MAX_SIZE = 3072

/**
 * Loads the image at [path] and redraws it upright (orientation applied) at scale 1, with the longer side at
 * most [maxSize] pixels, so `cgImage` pixel coordinates match what the user sees.
 */
internal fun loadUpright(path: String, maxSize: Int): UIImage? {
    val image = UIImage.imageWithContentsOfFile(path) ?: return null
    val (width, height) = image.size.useContents { width * image.scale to height * image.scale }
    if (width <= 0.0 || height <= 0.0) return null
    val factor = min(1.0, maxSize / max(width, height))
    return render(image, width * factor, height * factor)
}

internal fun render(image: UIImage, width: Double, height: Double): UIImage {
    val format = UIGraphicsImageRendererFormat().apply {
        scale = 1.0
        opaque = true
    }
    return UIGraphicsImageRenderer(size = CGSizeMake(width, height), format = format).imageWithActions {
        image.drawInRect(CGRectMake(0.0, 0.0, width, height))
    }
}

/** Vision rectangles are normalized with a bottom-left origin. */
internal fun CValue<CGRect>.visionToNormalized(): NormalizedRect = useContents {
    NormalizedRect(
        left = origin.x.toFloat(),
        top = (1.0 - origin.y - size.height).toFloat(),
        right = (origin.x + size.width).toFloat(),
        bottom = (1.0 - origin.y).toFloat(),
    )
}

internal fun NSData.toByteArray(): ByteArray = bytes?.readBytes(length.toInt()) ?: ByteArray(0)

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.dataWithBytes(it.addressOf(0), size.toULong()) }
