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
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
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
    /** PNG cut out of the photo for the card banner, `null` when it could not be produced. */
    val banner: StoredImage?,
)

/** Runs code detection, OCR and colour extraction on a photo in parallel and merges the results. */
internal class ImageAnalyzer(
    private val barcodeDetector: BarcodeDetector,
    private val textRecognizer: TextRecognizer,
    private val imageProcessor: ImageProcessor,
    private val imageStore: ImageStore,
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
        val bannerRegion = BannerRegion.pick(pixels.width.toFloat() / pixels.height, nonEmptyCodes)
        CardSuggestion(
            image = image,
            codes = nonEmptyCodes,
            names = CardNameSuggester.suggest(lines, nonEmptyCodes),
            colors = ColorPalette.extract(pixels).ifEmpty { listOf(CardColor.Default) },
            // The banner is decoration; a card without one is still a good card.
            banner = either {
                val png = imageProcessor.cropToPng(image, bannerRegion, targetWidth = BANNER_WIDTH).bind()
                imageStore.save(png).bind()
            }.getOrNull(),
        )
    }

    private companion object {
        /** Apple's strip image @3x. */
        const val BANNER_WIDTH = 1125
    }
}
