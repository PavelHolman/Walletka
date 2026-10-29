package cz.poqet.walletka.data

import arrow.core.Either
import arrow.core.NonEmptyList
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.core.toNonEmptyListOrNull
import arrow.fx.coroutines.parZip
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.TextLine
import cz.poqet.walletka.platform.BarcodeDetector
import cz.poqet.walletka.platform.ImageProcessor
import cz.poqet.walletka.platform.ImageStore
import cz.poqet.walletka.platform.TextRecognizer
import kotlin.coroutines.EmptyCoroutineContext

/** What the app proposes for a new card, before the user edits it. */
internal data class CardSuggestion(
    val image: StoredImage,
    val codes: NonEmptyList<ScannedCode>,
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

/** Runs code detection, OCR and colour extraction on a photo in parallel and merges the results. */
internal class ImageAnalyzer(
    private val barcodeDetector: BarcodeDetector,
    private val textRecognizer: TextRecognizer,
    private val imageProcessor: ImageProcessor,
    private val bannerCropper: BannerCropper,
) {

    suspend fun analyze(image: StoredImage): Either<AnalysisError, CardSuggestion> = either {
        // Platform detectors move to their own background dispatchers, so the fan-out stays on the caller's context.
        val (codes, lines, pixels) = parZip(
            EmptyCoroutineContext,
            { barcodeDetector.detect(image).bind() },
            // OCR only improves the name suggestion, so its failure must not fail the analysis.
            { textRecognizer.recognize(image).getOrNull().orEmpty() },
            { imageProcessor.pixels(image, maxSize = 96).bind() },
        ) { codes, lines, pixels -> Triple(codes, lines, pixels) }

        val nonEmptyCodes = ensureNotNull(codes.distinctBy { it.format to it.value }.toNonEmptyListOrNull()) {
            AnalysisError(AnalysisFailure.NO_CODE_FOUND)
        }
        val photoAspectRatio = pixels.width.toFloat() / pixels.height
        val bannerRegion = BannerRegion.pick(photoAspectRatio, nonEmptyCodes, lines)
        CardSuggestion(
            image = image,
            codes = nonEmptyCodes,
            names = CardNameSuggester.suggest(lines, nonEmptyCodes),
            colors = ColorPalette.extract(pixels).ifEmpty { listOf(CardColor.Default) },
            photoAspectRatio = photoAspectRatio,
            bannerRegion = bannerRegion,
            // The banner is decoration; a card without one is still a good card.
            banner = bannerCropper.crop(image, bannerRegion).getOrNull(),
            lines = lines,
        )
    }
}
