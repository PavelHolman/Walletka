package cz.poqet.walletka.platform

import arrow.core.Either
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.TextLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File

private suspend fun <T> detect(image: StoredImage, block: suspend (InputImage, width: Int, height: Int) -> T): Either<AnalysisError, T> =
    Either.catch {
        val bitmap = withContext(Dispatchers.IO) { decodeUpright(File(image.path), DETECTION_MAX_SIZE) }
        block(InputImage.fromBitmap(bitmap, 0), bitmap.width, bitmap.height)
    }.mapLeft { AnalysisError(AnalysisFailure.DETECTION_FAILED, it.message) }

/** ML Kit barcode scanning, all formats. */
internal class MlKitBarcodeDetector : BarcodeDetector {

    private val scanner by lazy { BarcodeScanning.getClient() }

    override suspend fun detect(image: StoredImage): Either<AnalysisError, List<ScannedCode>> =
        detect(image) { input, width, height ->
            scanner.process(input).await().mapNotNull { barcode ->
                val format = barcode.format.toCodeFormat() ?: return@mapNotNull null
                val value = barcode.rawValue?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                ScannedCode(format, value, barcode.boundingBox?.normalizedIn(width, height))
            }
        }

    private fun Int.toCodeFormat(): CodeFormat? = when (this) {
        Barcode.FORMAT_QR_CODE -> CodeFormat.QR
        Barcode.FORMAT_AZTEC -> CodeFormat.AZTEC
        Barcode.FORMAT_PDF417 -> CodeFormat.PDF417
        Barcode.FORMAT_DATA_MATRIX -> CodeFormat.DATA_MATRIX
        Barcode.FORMAT_CODE_128 -> CodeFormat.CODE_128
        Barcode.FORMAT_CODE_39 -> CodeFormat.CODE_39
        Barcode.FORMAT_CODE_93 -> CodeFormat.CODE_93
        Barcode.FORMAT_CODABAR -> CodeFormat.CODABAR
        Barcode.FORMAT_EAN_13 -> CodeFormat.EAN_13
        Barcode.FORMAT_EAN_8 -> CodeFormat.EAN_8
        Barcode.FORMAT_UPC_A -> CodeFormat.UPC_A
        Barcode.FORMAT_UPC_E -> CodeFormat.UPC_E
        Barcode.FORMAT_ITF -> CodeFormat.ITF
        else -> null
    }
}

/** ML Kit on-device Latin text recognition. */
internal class MlKitTextRecognizer : TextRecognizer {

    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    override suspend fun recognize(image: StoredImage): Either<AnalysisError, List<TextLine>> =
        detect(image) { input, width, height ->
            recognizer.process(input).await().textBlocks
                .flatMap { it.lines }
                .mapNotNull { line -> line.boundingBox?.let { TextLine(line.text, it.normalizedIn(width, height)) } }
        }
}
