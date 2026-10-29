package cz.poqet.walletka.component

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CardDraft
import cz.poqet.walletka.domain.ImagePixels
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScanVariant
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.domain.TextLine
import cz.poqet.walletka.domain.WalletError
import cz.poqet.walletka.platform.AddedCard
import cz.poqet.walletka.platform.ImageProcessor
import cz.poqet.walletka.platform.ImageStore
import cz.poqet.walletka.platform.PlatformServices
import cz.poqet.walletka.platform.WalletPlatform
import cz.poqet.walletka.platform.WalletService
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.files.SystemTemporaryDirectory
import kotlinx.io.writeString
import kotlin.random.Random

/** Writes real files (the card library copies them) into a fresh temporary directory. */
internal class FakeImageStore(val directory: Path = tempDirectory("images")) : ImageStore {
    val deleted = mutableListOf<StoredImage>()
    private var banners = 0

    override suspend fun import(source: String): Either<AnalysisError, StoredImage> =
        if (source == "broken") AnalysisError(AnalysisFailure.IMAGE_UNREADABLE).left() else write(source).right()

    override suspend fun save(png: ByteArray) = write("banner${banners++}.png").right()

    override suspend fun delete(image: StoredImage) {
        deleted += image
        SystemFileSystem.delete(Path(image.path), mustExist = false)
    }

    fun path(name: String) = Path(directory, name).toString()

    private fun write(name: String): StoredImage {
        val path = Path(directory, name)
        SystemFileSystem.sink(path).buffered().use { it.writeString(name) }
        return StoredImage(path.toString())
    }
}

internal fun tempDirectory(name: String): Path =
    Path(SystemTemporaryDirectory, "walletka-test-$name-${Random.nextLong().toULong()}").also { SystemFileSystem.createDirectories(it) }

internal class FakeImageProcessor : ImageProcessor {
    override suspend fun pixels(image: StoredImage, maxSize: Int) =
        ImagePixels(2, 2, IntArray(4) { 0xFF1A237E.toInt() }).right()

    override suspend fun cropToPng(image: StoredImage, region: NormalizedRect, targetWidth: Int) = ByteArray(1).right()

    override suspend fun renderForScan(image: StoredImage, variant: ScanVariant) = ByteArray(1).right()
}

internal class FakeWalletService(var result: Either<WalletError, AddedCard> = AddedCard("wallet://card").right()) : WalletService {
    val added = mutableListOf<CardDraft>()
    override val platform = WalletPlatform.GOOGLE_WALLET
    override suspend fun isAvailable() = true
    val removed = mutableListOf<String>()
    val updated = mutableListOf<CardDraft>()
    override var canUpdateInPlace = false
    var inWallet = true

    /** Wallet ids of the passes in the wallet; `null` when the wallet can't tell (as Google Wallet). */
    var passesInWallet: Set<String>? = null

    override suspend fun isInWallet(walletId: String): Boolean? = passesInWallet?.let { walletId in it }

    override suspend fun updateCard(card: CardDraft): Either<WalletError, AddedCard> {
        if (!inWallet) return WalletError(cz.poqet.walletka.domain.WalletFailure.NOT_IN_WALLET).left()
        updated += card
        return AddedCard(replacedExisting = true).right()
    }

    override suspend fun removeCard(walletId: String): Boolean {
        removed += walletId
        return true
    }

    override suspend fun addCard(card: CardDraft): Either<WalletError, AddedCard> {
        added += card
        return result
    }
}

internal fun fakeServices(
    codes: List<ScannedCode>,
    lines: List<TextLine> = emptyList(),
    imageStore: FakeImageStore = FakeImageStore(),
    walletService: FakeWalletService = FakeWalletService(),
    dataDirectory: Path = tempDirectory("data"),
) = PlatformServices(
    imageStore = imageStore,
    barcodeDetector = { codes.right() },
    textRecognizer = { lines.right() },
    imageProcessor = FakeImageProcessor(),
    walletService = walletService,
    dataDirectory = dataDirectory.toString(),
    ioContext = kotlin.coroutines.EmptyCoroutineContext,
)
