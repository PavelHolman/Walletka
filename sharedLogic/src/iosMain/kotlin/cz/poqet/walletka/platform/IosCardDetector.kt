package cz.poqet.walletka.platform

import cz.poqet.walletka.data.CardFraming
import cz.poqet.walletka.domain.NormalizedPoint
import cz.poqet.walletka.domain.NormalizedQuad
import cz.poqet.walletka.domain.StoredImage
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGPoint
import platform.CoreImage.CIContext
import platform.CoreImage.CIDetector
import platform.CoreImage.CIDetectorAccuracy
import platform.CoreImage.CIDetectorAccuracyHigh
import platform.CoreImage.CIDetectorTypeRectangle
import platform.CoreImage.CIFilter
import platform.CoreImage.CIImage
import platform.CoreImage.CIRectangleFeature
import platform.CoreImage.CIVector
import platform.CoreImage.createCGImage
import platform.CoreImage.filterWithName
import platform.CoreImage.kCIInputImageKey
import platform.Foundation.NSError
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.Vision.VNDetectRectanglesRequest
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRectangleObservation

/**
 * Finds cards with Vision's rectangle detection (Core Image's detector where Vision has no model, e.g. on the
 * Simulator) and straightens them with `CIPerspectiveCorrection`.
 */
internal class IosCardDetector(private val imageStore: ImageStore) : CardDetector {

    override suspend fun outlines(image: StoredImage): List<NormalizedQuad> = withContext(Dispatchers.Default) {
        val cgImage = loadUpright(image.path, OUTLINE_MAX_SIZE)?.CGImage ?: return@withContext emptyList()
        detectWithVision(cgImage).ifEmpty { detectWithCoreImage(cgImage) }
    }

    override suspend fun straighten(image: StoredImage, quad: NormalizedQuad): StoredImage? {
        val jpeg = withContext(Dispatchers.Default) { straightenedJpeg(image, quad) } ?: return null
        return imageStore.save(jpeg).getOrNull()
    }

    private fun detectWithVision(cgImage: CGImageRef): List<NormalizedQuad> {
        val request = VNDetectRectanglesRequest().apply {
            minimumSize = 0.3f
            minimumAspectRatio = 0.3f
            maximumAspectRatio = 1f
            quadratureTolerance = 25f
            minimumConfidence = 0.5f
            maximumObservations = 6u
        }
        val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
        val ok = memScoped {
            val error = alloc<ObjCObjectVar<NSError?>>()
            handler.performRequests(listOf(request), error.ptr)
        }
        if (!ok) return emptyList()
        return request.results.orEmpty().filterIsInstance<VNRectangleObservation>()
            .sortedByDescending { it.confidence }
            .map { NormalizedQuad(it.topLeft.flipped(), it.topRight.flipped(), it.bottomRight.flipped(), it.bottomLeft.flipped()) }
    }

    private fun detectWithCoreImage(cgImage: CGImageRef): List<NormalizedQuad> {
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()
        val detector = CIDetector.detectorOfType(
            CIDetectorTypeRectangle,
            context = null,
            options = mapOf<Any?, Any?>(CIDetectorAccuracy to CIDetectorAccuracyHigh),
        ) ?: return emptyList()
        return detector.featuresInImage(CIImage(cGImage = cgImage)).filterIsInstance<CIRectangleFeature>().map { feature ->
            fun CValue<CGPoint>.normalized() = useContents { NormalizedPoint((x / width).toFloat(), (1 - y / height).toFloat()) }
            NormalizedQuad(feature.topLeft.normalized(), feature.topRight.normalized(), feature.bottomRight.normalized(), feature.bottomLeft.normalized())
        }
    }

    private fun straightenedJpeg(image: StoredImage, quad: NormalizedQuad): ByteArray? {
        val cgImage = loadUpright(image.path, CARD_MAX_SIZE)?.CGImage ?: return null
        val width = CGImageGetWidth(cgImage).toDouble()
        val height = CGImageGetHeight(cgImage).toDouble()
        // Core Image coordinates are in pixels with a bottom-left origin.
        fun NormalizedPoint.vector() = CIVector.vectorWithX(x * width, Y = (1 - y) * height)
        val filter = CIFilter.filterWithName(
            "CIPerspectiveCorrection",
            withInputParameters = mapOf<Any?, Any?>(
                kCIInputImageKey to CIImage(cGImage = cgImage),
                "inputTopLeft" to quad.topLeft.vector(),
                "inputTopRight" to quad.topRight.vector(),
                "inputBottomRight" to quad.bottomRight.vector(),
                "inputBottomLeft" to quad.bottomLeft.vector(),
            ),
        ) ?: return null
        val output = filter.outputImage ?: return null
        val straightened = CIContext.contextWithOptions(null).createCGImage(output, fromRect = output.extent) ?: return null
        val result = UIImage(cGImage = straightened)
        CGImageRelease(straightened)
        // Large enough for the banner and logo crops, without keeping a 12 MP photo around.
        val (cardWidth, cardHeight) = CardFraming.straightenedSize(quad, width.toInt(), height.toInt())
        if (cardWidth < MIN_CARD_SIZE || cardHeight < MIN_CARD_SIZE) return null
        return UIImageJPEGRepresentation(result, 0.92)?.toByteArray()
    }

    /** Vision points are normalized with a bottom-left origin. */
    private fun CValue<CGPoint>.flipped() = useContents { NormalizedPoint(x.toFloat(), (1 - y).toFloat()) }

    private companion object {
        /** Outlines don't need detail. */
        const val OUTLINE_MAX_SIZE = 1024

        /** The straightened card keeps enough resolution for a sharp @3x banner (1125 px wide). */
        const val CARD_MAX_SIZE = 3072

        /** Smaller cut-outs are too blurry to be worth it. */
        const val MIN_CARD_SIZE = 400
    }
}
