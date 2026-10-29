package cz.poqet.walletka.data

import arrow.core.raise.either
import cz.poqet.walletka.domain.ScanVariant
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.platform.BarcodeDetector
import cz.poqet.walletka.platform.ImageProcessor
import cz.poqet.walletka.platform.ImageStore
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Retries barcode detection on processed copies of a photo when the plain pass found nothing: contrast-boosted
 * greyscale (glare, faded or coloured print), upscaled crops (small or dense codes) and rotations (codes
 * photographed at an angle). Stops at the first variant with a code, or when [budget] runs out.
 */
internal class ScanFallbacks(
    private val barcodeDetector: BarcodeDetector,
    private val imageProcessor: ImageProcessor,
    private val imageStore: ImageStore,
    private val variants: List<ScanVariant> = DefaultVariants,
    private val budget: Duration = 6.seconds,
) {

    /** Codes found in the first variant that has any, with bounds in [image]'s coordinates; empty when none has. */
    suspend fun scan(image: StoredImage, photoAspectRatio: Float): List<ScannedCode> =
        withTimeoutOrNull(budget) {
            variants.firstNotNullOfOrNull { variant ->
                detect(image, variant)
                    .takeIf { it.isNotEmpty() }
                    ?.map { code -> code.copy(bounds = code.bounds?.let { variant.toPhoto(it, photoAspectRatio) }) }
            }
        }.orEmpty()

    /** A variant that can't be rendered or scanned just counts as having no code. */
    private suspend fun detect(image: StoredImage, variant: ScanVariant): List<ScannedCode> {
        val rendered = either {
            imageStore.save(imageProcessor.renderForScan(image, variant).bind()).bind()
        }.getOrNull() ?: return emptyList()
        return try {
            barcodeDetector.detect(rendered).getOrNull().orEmpty()
        } finally {
            withContext(NonCancellable) { imageStore.delete(rendered) }
        }
    }

    companion object {
        /** Cheapest and most likely first. */
        val DefaultVariants: List<ScanVariant> = buildList {
            add(ScanVariant(enhanced = true))
            add(ScanVariant(region = ScanVariant.Centre))
            ScanVariant.tiles().forEach { add(ScanVariant(region = it)) }
            add(ScanVariant(rotationDegrees = 20f))
            add(ScanVariant(rotationDegrees = -20f))
        }
    }
}
