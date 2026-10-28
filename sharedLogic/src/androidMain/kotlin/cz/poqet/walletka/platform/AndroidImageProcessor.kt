package cz.poqet.walletka.platform

import android.graphics.Bitmap
import arrow.core.Either
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.StoredImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.roundToInt

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

    private suspend fun <T> io(block: () -> T): Either<AnalysisError, T> = withContext(Dispatchers.Default) {
        Either.catch(block).mapLeft { AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, it.message) }
    }
}
