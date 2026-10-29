package cz.poqet.walletka.platform

import arrow.core.Either
import arrow.core.right
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.TextLine
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.cinterop.value
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeQRCode
import platform.CoreImage.CIImage
import platform.CoreImage.CIQRCodeFeature
import platform.Foundation.NSError
import platform.Vision.VNBarcodeObservation
import platform.Vision.VNBarcodeSymbologyAztec
import platform.Vision.VNBarcodeSymbologyCodabar
import platform.Vision.VNBarcodeSymbologyCode128
import platform.Vision.VNBarcodeSymbologyCode39
import platform.Vision.VNBarcodeSymbologyCode39Checksum
import platform.Vision.VNBarcodeSymbologyCode39FullASCII
import platform.Vision.VNBarcodeSymbologyCode39FullASCIIChecksum
import platform.Vision.VNBarcodeSymbologyCode93
import platform.Vision.VNBarcodeSymbologyCode93i
import platform.Vision.VNBarcodeSymbologyDataMatrix
import platform.Vision.VNBarcodeSymbologyEAN13
import platform.Vision.VNBarcodeSymbologyEAN8
import platform.Vision.VNBarcodeSymbologyI2of5
import platform.Vision.VNBarcodeSymbologyI2of5Checksum
import platform.Vision.VNBarcodeSymbologyITF14
import platform.Vision.VNBarcodeSymbologyMicroQR
import platform.Vision.VNBarcodeSymbologyPDF417
import platform.Vision.VNBarcodeSymbologyQR
import platform.Vision.VNBarcodeSymbologyUPCE
import platform.Vision.VNDetectBarcodesRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequest
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/** Runs a Vision [request] on the upright image and returns its results. */
private suspend fun perform(image: StoredImage, request: VNRequest): Either<AnalysisError, List<*>> =
    withContext(Dispatchers.Default) {
        either {
            val cgImage = ensureNotNull(loadUpright(image.path, DETECTION_MAX_SIZE)?.CGImage) {
                AnalysisError(AnalysisFailure.IMAGE_UNREADABLE)
            }
            val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                val ok = handler.performRequests(listOf(request), error.ptr)
                ensure(ok) { AnalysisError(AnalysisFailure.DETECTION_FAILED, error.value?.localizedDescription) }
            }
            request.results.orEmpty()
        }
    }

/**
 * Vision barcode detection (all symbologies the OS supports), with Core Image's QR detector as a fallback when
 * Vision fails or finds nothing. Vision's barcode models need a GPU/Neural Engine inference context, which the
 * iOS Simulator does not provide.
 */
internal class VisionBarcodeDetector : BarcodeDetector {

    override suspend fun detect(image: StoredImage): Either<AnalysisError, List<ScannedCode>> {
        val vision = detectWithVision(image)
        if (vision.getOrNull()?.isNotEmpty() == true) return vision
        val coreImage = detectQrWithCoreImage(image)
        return if (coreImage.isNotEmpty()) coreImage.right() else vision
    }

    private suspend fun detectWithVision(image: StoredImage): Either<AnalysisError, List<ScannedCode>> =
        perform(image, VNDetectBarcodesRequest()).map { results ->
            results.filterIsInstance<VNBarcodeObservation>().mapNotNull { observation ->
                val value = observation.payloadStringValue?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                val format = observation.symbology?.toCodeFormat(value) ?: return@mapNotNull null
                ScannedCode(
                    format = format,
                    // Vision reports UPC-A as EAN-13 with a leading zero.
                    value = if (format == CodeFormat.UPC_A) value.drop(1) else value,
                    bounds = observation.boundingBox.visionToNormalized(),
                )
            }
        }

    private suspend fun detectQrWithCoreImage(image: StoredImage): List<ScannedCode> = withContext(Dispatchers.Default) {
        val cgImage = loadUpright(image.path, DETECTION_MAX_SIZE)?.CGImage ?: return@withContext emptyList()
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()
        val detector = CIDetector.detectorOfType(
            CIDetectorTypeQRCode,
            context = null,
            options = mapOf<Any?, Any?>(CIDetectorAccuracy to CIDetectorAccuracyHigh),
        ) ?: return@withContext emptyList()
        detector.featuresInImage(CIImage(cGImage = cgImage)).filterIsInstance<CIQRCodeFeature>().mapNotNull { feature ->
            val value = feature.messageString?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            // Core Image uses pixels with a bottom-left origin.
            val bounds = feature.bounds.useContents {
                NormalizedRect(
                    left = (origin.x / width).toFloat(),
                    top = (1.0 - (origin.y + size.height) / height).toFloat(),
                    right = ((origin.x + size.width) / width).toFloat(),
                    bottom = (1.0 - origin.y / height).toFloat(),
                )
            }
            ScannedCode(CodeFormat.QR, value, bounds)
        }
    }

    private fun String.toCodeFormat(value: String): CodeFormat? = when (this) {
        VNBarcodeSymbologyQR, VNBarcodeSymbologyMicroQR -> CodeFormat.QR
        VNBarcodeSymbologyAztec -> CodeFormat.AZTEC
        VNBarcodeSymbologyPDF417 -> CodeFormat.PDF417
        VNBarcodeSymbologyDataMatrix -> CodeFormat.DATA_MATRIX
        VNBarcodeSymbologyCode128 -> CodeFormat.CODE_128
        VNBarcodeSymbologyCode39, VNBarcodeSymbologyCode39Checksum,
        VNBarcodeSymbologyCode39FullASCII, VNBarcodeSymbologyCode39FullASCIIChecksum -> CodeFormat.CODE_39
        VNBarcodeSymbologyCode93, VNBarcodeSymbologyCode93i -> CodeFormat.CODE_93
        VNBarcodeSymbologyCodabar -> CodeFormat.CODABAR
        VNBarcodeSymbologyEAN13 -> if (value.length == 13 && value.startsWith('0')) CodeFormat.UPC_A else CodeFormat.EAN_13
        VNBarcodeSymbologyEAN8 -> CodeFormat.EAN_8
        VNBarcodeSymbologyUPCE -> CodeFormat.UPC_E
        VNBarcodeSymbologyITF14, VNBarcodeSymbologyI2of5, VNBarcodeSymbologyI2of5Checksum -> CodeFormat.ITF
        else -> null
    }
}

/** Vision on-device text recognition. */
internal class VisionTextRecognizer : TextRecognizer {

    override suspend fun recognize(image: StoredImage): Either<AnalysisError, List<TextLine>> {
        val request = VNRecognizeTextRequest().apply {
            recognitionLevel = VNRequestTextRecognitionLevelAccurate
            usesLanguageCorrection = true
            // Czech cards carry diacritics that the default English-only correction would strip.
            automaticallyDetectsLanguage = true
        }
        return perform(image, request).map { results ->
            results.filterIsInstance<VNRecognizedTextObservation>().mapNotNull { observation ->
                val candidate = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText
                candidate?.let { TextLine(it.string, observation.boundingBox.visionToNormalized(), it.confidence) }
            }
        }
    }
}
