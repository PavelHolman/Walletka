package cz.poqet.walletka.platform

import arrow.core.Either
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScanVariant
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.TextLine
import cz.poqet.walletka.domain.WalletError
import kotlinx.coroutines.Dispatchers
import kotlin.coroutines.CoroutineContext

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

    /** Renders [variant] of the image for another detection attempt, encoded as JPEG. */
    suspend fun renderForScan(image: StoredImage, variant: ScanVariant): Either<AnalysisError, ByteArray>
}

enum class WalletPlatform { APPLE_WALLET, GOOGLE_WALLET }

/** Issues a pass into the platform wallet and drives the system "add pass" UI. */
internal interface WalletService {
    val platform: WalletPlatform

    suspend fun isAvailable(): Boolean

    /** Adds the card, or replaces the pass with the same [CardDraft.walletId] where the wallet allows it. */
    suspend fun addCard(card: CardDraft): Either<WalletError, AddedCard>

    /** Whether [updateCard] can replace a pass without any UI (Apple Wallet yes, the on-device Google API no). */
    val canUpdateInPlace: Boolean

    /** Silently replaces the pass with the same [CardDraft.walletId]; fails with `NOT_IN_WALLET` if it's gone. */
    suspend fun updateCard(card: CardDraft): Either<WalletError, AddedCard>

    /** Removes the pass from the wallet; `false` when the platform does not let apps do that. */
    suspend fun removeCard(walletId: String): Boolean
}

/** A card that is now in the wallet. */
internal data class AddedCard(
    /** Opens the card in the wallet app, when the platform offers such a link. */
    val walletUrl: String? = null,
    /** An existing pass was updated in place rather than a new one added. */
    val replacedExisting: Boolean = false,
)

/** The platform specific implementations the shared logic depends on. */
internal class PlatformServices(
    val imageStore: ImageStore,
    val barcodeDetector: BarcodeDetector,
    val textRecognizer: TextRecognizer,
    val imageProcessor: ImageProcessor,
    val walletService: WalletService,
    /** App-private directory for persistent data (the card library). */
    val dataDirectory: String,
    /** Where blocking file I/O runs. */
    val ioContext: CoroutineContext = Dispatchers.Default,
)
