package cz.poqet.walletka.platform

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import cz.poqet.walletka.domain.NormalizedRect
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

/** Decodes [file] upright (EXIF orientation applied) with the longer side at most [maxSize] pixels. */
internal fun decodeUpright(file: File, maxSize: Int): Bitmap =
    ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
        val longer = max(info.size.width, info.size.height)
        if (longer > maxSize) {
            val scale = maxSize.toFloat() / longer
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        decoder.isMutableRequired = false
    }

internal fun android.graphics.Rect.normalizedIn(width: Int, height: Int) = NormalizedRect(
    left = left.toFloat() / width,
    top = top.toFloat() / height,
    right = right.toFloat() / width,
    bottom = bottom.toFloat() / height,
)

/** Max resolution fed to the ML Kit detectors; plenty for codes, keeps memory bounded. */
internal const val DETECTION_MAX_SIZE = 2048

/** Max resolution a photo is decoded at for an upscaled crop in a scan retry. */
internal const val SCAN_SOURCE_MAX_SIZE = 3072
