package cz.poqet.walletka.data

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parZip
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.TextLine
import cz.poqet.walletka.platform.BarcodeDetector
import cz.poqet.walletka.platform.CardDetector
import cz.poqet.walletka.platform.ImageProcessor
import cz.poqet.walletka.platform.ImageStore
import cz.poqet.walletka.platform.TextRecognizer
import kotlin.coroutines.EmptyCoroutineContext

/** What the app proposes for a new card, before the user edits it. */
internal data class CardSuggestion(
    val image: StoredImage,
    /** Empty when no code was found, so the user types it in. */
    val codes: List<ScannedCode>,
    val names: List<String>,
    val colors: List<CardColor>,
    val photoAspectRatio: Float,
    val bannerRegion: NormalizedRect,
    /** PNG cut out of the photo for the card banner, `null` when it could not be produced. */
    val banner: StoredImage?,
    /** Text found in the photo, for placing crops (e.g. the logo) that depend on the wallet. */
    val lines: List<TextLine> = emptyList(),
)

/** Cuts the banner (or another crop, such as the logo) out of the photo and stores it as a PNG. */
internal class BannerCropper(
    private val imageProcessor: ImageProcessor,
    private val imageStore: ImageStore,
) {
    suspend fun crop(
        image: StoredImage,
        region: NormalizedRect,
        targetWidth: Int = BANNER_WIDTH,
    ): Either<AnalysisError, StoredImage> = either {
        val png = imageProcessor.cropToPng(image, region, targetWidth).bind()
        imageStore.save(png).bind()
    }

    private companion object {
        /** Apple's strip image @3x. */
        const val BANNER_WIDTH = 1125
    }
}

/**
 * Runs code detection, OCR and colour extraction on a photo in parallel and merges the results. When the photo shows
 * the card on a background, the card is cut out first ([CardFraming]). When the plain detection finds no code,
 * [ScanFallbacks] retries on processed copies of the photo.
 */
internal class ImageAnalyzer(
    private val barcodeDetector: BarcodeDetector,
    private val textRecognizer: TextRecognizer,
    private val imageProcessor: ImageProcessor,
    private val bannerCropper: BannerCropper,
    private val scanFallbacks: ScanFallbacks,
    private val cardDetector: CardDetector = CardDetector.None,
    private val imageStore: ImageStore? = null,
) {

    private class Detection(val codes: List<ScannedCode>, val lines: List<TextLine>, val pixels: ImagePixels)

    suspend fun analyze(photo: StoredImage): Either<AnalysisError, CardSuggestion> = either {
        val original = detect(photo).bind()
        // Only the card is kept when it can be cut out without losing its code.
        val card = straightenedCard(photo, original)
        val (image, detection) = card ?: (photo to original)

        val photoAspectRatio = detection.pixels.width.toFloat() / detection.pixels.height
        val foundCodes = detection.codes.ifEmpty { scanFallbacks.scan(image, photoAspectRatio) }.distinctBy { it.format to it.value }
        val bannerRegion = BannerRegion.pick(photoAspectRatio, foundCodes, detection.lines)
        CardSuggestion(
            image = image,
            codes = foundCodes,
            names = CardNameSuggester.suggest(detection.lines, foundCodes),
            colors = ColorPalette.extract(detection.pixels).ifEmpty { listOf(CardColor.Default) },
            photoAspectRatio = photoAspectRatio,
            bannerRegion = bannerRegion,
            // The banner is decoration; a card without one is still a good card.
            banner = bannerCropper.crop(image, bannerRegion).getOrNull(),
            lines = detection.lines,
        )
    }

    private suspend fun detect(image: StoredImage): Either<AnalysisError, Detection> = either {
        // Platform detectors move to their own background dispatchers, so the fan-out stays on the caller's context.
        parZip(
            EmptyCoroutineContext,
            // A detector failure is treated like a photo without a code: the user can still type it in.
            { barcodeDetector.detect(image).getOrNull().orEmpty() },
            // OCR only improves the name suggestion, so its failure must not fail the analysis.
            { textRecognizer.recognize(image).getOrNull().orEmpty() },
            { imageProcessor.pixels(image, maxSize = 96).bind() },
        ) { codes, lines, pixels -> Detection(codes, lines, pixels) }
    }

    /** The card cut out of [photo] and its detection, or `null` to keep the whole photo. */
    private suspend fun straightenedCard(photo: StoredImage, original: Detection): Pair<StoredImage, Detection>? {
        val aspectRatio = original.pixels.width.toFloat() / original.pixels.height
        val quad = cardDetector.outlines(photo).firstOrNull { CardFraming.isCard(it, aspectRatio, original.codes) } ?: return null
        val card = cardDetector.straighten(photo, CardFraming.inset(quad)) ?: return null
        val detection = detect(card).getOrNull()
        // Straightening must not cost the code (or the image): then the whole photo is better.
        if (detection == null || detection.codes.size < original.codes.size) {
            imageStore?.delete(card)
            return null
        }
        imageStore?.delete(photo)
        return card to detection
    }
}
