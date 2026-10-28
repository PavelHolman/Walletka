package cz.poqet.walletka.platform

import arrow.core.Either
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.TextLine
import cz.poqet.walletka.domain.WalletError

/**
 * Copies images coming from outside the app (share sheet, clipboard, photo picker) into app storage.
 *
 * `source` is platform specific: a `content://`/`file://` URI on Android, a file path or `file://` URL on iOS.
 */
internal interface ImageStore {
    suspend fun import(source: String): Either<AnalysisError, StoredImage>

    suspend fun save(png: ByteArray): Either<AnalysisError, StoredImage>

    suspend fun delete(image: StoredImage)
}

/** Finds barcodes and 2D codes in an image (ML Kit on Android, Vision on iOS). */
internal fun interface BarcodeDetector {
    suspend fun detect(image: StoredImage): Either<AnalysisError, List<ScannedCode>>
}

/** On-device OCR (ML Kit on Android, Vision on iOS). */
internal fun interface TextRecognizer {
    suspend fun recognize(image: StoredImage): Either<AnalysisError, List<TextLine>>
}

/** Pixel level operations that need the platform's image codecs. */
internal interface ImageProcessor {
    /** Decodes the image (honouring EXIF orientation), downscaled so the longer side is at most [maxSize]. */
    suspend fun pixels(image: StoredImage, maxSize: Int): Either<AnalysisError, ImagePixels>

    /** Crops [region] out of the image, scales it to [targetWidth] and encodes it as PNG. */
    suspend fun cropToPng(image: StoredImage, region: NormalizedRect, targetWidth: Int): Either<AnalysisError, ByteArray>
}

enum class WalletPlatform { APPLE_WALLET, GOOGLE_WALLET }

/** Issues a pass into the platform wallet and drives the system "add pass" UI. */
internal interface WalletService {
    val platform: WalletPlatform

    suspend fun isAvailable(): Boolean

    suspend fun addCard(card: CardDraft): Either<WalletError, Unit>
}

/** The platform specific implementations the shared logic depends on. */
internal class PlatformServices(
    val imageStore: ImageStore,
    val barcodeDetector: BarcodeDetector,
    val textRecognizer: TextRecognizer,
    val imageProcessor: ImageProcessor,
    val walletService: WalletService,
)
