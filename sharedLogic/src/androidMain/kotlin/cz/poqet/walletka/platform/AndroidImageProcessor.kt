package cz.poqet.walletka.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import arrow.core.Either
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScanVariant
import cz.poqet.walletka.domain.StoredImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

internal class AndroidImageProcessor : ImageProcessor {

    override suspend fun pixels(image: StoredImage, maxSize: Int): Either<AnalysisError, ImagePixels> = io {
        val bitmap = decodeUpright(File(image.path), maxSize)
        val argb = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(argb, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        ImagePixels(bitmap.width, bitmap.height, argb)
    }

    override suspend fun cropToPng(
        image: StoredImage,
        region: NormalizedRect,
        targetWidth: Int,
    ): Either<AnalysisError, ByteArray> = io {
        val bitmap = decodeUpright(File(image.path), DETECTION_MAX_SIZE)
        val x = (region.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (region.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val w = (region.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
        val h = (region.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)
        val cropped = Bitmap.createBitmap(bitmap, x, y, w, h)
        val scaled = Bitmap.createScaledBitmap(cropped, targetWidth, (targetWidth * h.toFloat() / w).roundToInt().coerceAtLeast(1), true)
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
    }

    override suspend fun renderForScan(image: StoredImage, variant: ScanVariant): Either<AnalysisError, ByteArray> = io {
        val region = variant.region
        // Decode a crop at a higher resolution, so upscaling it adds real detail where the photo has it.
        val longerFraction = max(region.width, region.height).coerceAtLeast(0.05f)
        val bitmap = decodeUpright(File(image.path), (variant.maxSize / longerFraction).roundToInt().coerceAtMost(SCAN_SOURCE_MAX_SIZE))
        val x = (region.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (region.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val w = (region.width * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
        val h = (region.height * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)
        val scale = variant.maxSize.toFloat() / max(w, h)
        val angle = Math.toRadians(variant.rotationDegrees.toDouble())
        val scaledW = w * scale
        val scaledH = h * scale
        val outW = (abs(scaledW * cos(angle)) + abs(scaledH * sin(angle))).roundToInt().coerceAtLeast(1)
        val outH = (abs(scaledW * sin(angle)) + abs(scaledH * cos(angle))).roundToInt().coerceAtLeast(1)

        val output = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val matrix = Matrix().apply {
            // Crop origin to the centre of the output, scaled and rotated clockwise around it.
            postTranslate(-x - w / 2f, -y - h / 2f)
            postScale(scale, scale)
            postRotate(variant.rotationDegrees)
            postTranslate(outW / 2f, outH / 2f)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            if (variant.enhanced) colorFilter = ColorMatrixColorFilter(enhanceMatrix())
        }
        canvas.drawBitmap(bitmap, matrix, paint)
        ByteArrayOutputStream().use { out ->
            output.compress(Bitmap.CompressFormat.JPEG, 92, out)
            out.toByteArray()
        }
    }

    /** Greyscale with the contrast boosted around mid-grey. */
    private fun enhanceMatrix(): ColorMatrix {
        val contrast = 1.6f
        val offset = 128f * (1 - contrast)
        return ColorMatrix().apply { setSaturation(0f) }.apply {
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, offset,
                        0f, contrast, 0f, 0f, offset,
                        0f, 0f, contrast, 0f, offset,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
    }

    private suspend fun <T> io(block: () -> T): Either<AnalysisError, T> = withContext(Dispatchers.Default) {
        Either.catch(block).mapLeft { AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, it.message) }
    }
}
