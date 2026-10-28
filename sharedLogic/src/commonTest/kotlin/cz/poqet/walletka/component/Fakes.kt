package cz.poqet.walletka.component

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.TextLine
import cz.poqet.walletka.domain.WalletError
import cz.poqet.walletka.platform.ImageProcessor
import cz.poqet.walletka.platform.ImageStore
import cz.poqet.walletka.platform.PlatformServices
import cz.poqet.walletka.platform.WalletPlatform
import cz.poqet.walletka.platform.WalletService

internal class FakeImageStore : ImageStore {
    val deleted = mutableListOf<StoredImage>()
    override suspend fun import(source: String): Either<AnalysisError, StoredImage> =
        if (source == "broken") AnalysisError(AnalysisFailure.IMAGE_UNREADABLE).left() else StoredImage("/stored/$source").right()

    override suspend fun save(png: ByteArray) = StoredImage("/stored/banner.png").right()

    override suspend fun delete(image: StoredImage) {
        deleted += image
    }
}

internal class FakeImageProcessor : ImageProcessor {
    override suspend fun pixels(image: StoredImage, maxSize: Int) =
        ImagePixels(2, 2, IntArray(4) { 0xFF1A237E.toInt() }).right()

    override suspend fun cropToPng(image: StoredImage, region: NormalizedRect, targetWidth: Int) = ByteArray(1).right()
}

internal class FakeWalletService(var result: Either<WalletError, Unit> = Unit.right()) : WalletService {
    val added = mutableListOf<CardDraft>()
    override val platform = WalletPlatform.GOOGLE_WALLET
    override suspend fun isAvailable() = true
    override suspend fun addCard(card: CardDraft): Either<WalletError, Unit> {
        added += card
        return result
    }
}

internal fun fakeServices(
    codes: List<ScannedCode>,
    lines: List<TextLine> = emptyList(),
    imageStore: FakeImageStore = FakeImageStore(),
    walletService: FakeWalletService = FakeWalletService(),
) = PlatformServices(
    imageStore = imageStore,
    barcodeDetector = { codes.right() },
    textRecognizer = { lines.right() },
    imageProcessor = FakeImageProcessor(),
    walletService = walletService,
)
