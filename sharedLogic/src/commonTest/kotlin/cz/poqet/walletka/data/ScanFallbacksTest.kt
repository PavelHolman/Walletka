package cz.poqet.walletka.data

import arrow.core.left
import arrow.core.right
import cz.poqet.walletka.component.FakeImageProcessor
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.domain.ScanVariant
import cz.poqet.walletka.domain.ScannedCode
import cz.poqet.walletka.domain.StoredImage
import cz.poqet.walletka.platform.ImageProcessor
import cz.poqet.walletka.platform.ImageStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class ScanFallbacksTest {

    private val photo = StoredImage("photo.jpg")
    private val enhanced = ScanVariant(enhanced = true)
    private val topLeft = ScanVariant(region = NormalizedRect(0f, 0f, 0.5f, 0.5f))
    private val rotated = ScanVariant(rotationDegrees = 90f)

    /** Keeps renders in memory: a saved image's path is the rendered content. */
    private class VariantStore : ImageStore {
        val saved = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        override suspend fun import(source: String) = StoredImage(source).right()
        override suspend fun save(png: ByteArray) = StoredImage(png.decodeToString()).also { saved += it.path }.right()
        override suspend fun delete(image: StoredImage) {
            deleted += image.path
        }
    }

    /** Renders a variant as `variant<its index in variants>`. */
    private class IndexingProcessor(private val variants: List<ScanVariant>) : ImageProcessor by FakeImageProcessor() {
        override suspend fun renderForScan(image: StoredImage, variant: ScanVariant) =
            "variant${variants.indexOf(variant)}".encodeToByteArray().right()
    }

    @Test
    fun stopsAtTheFirstVariantWithACodeAndMapsItsBounds() = runTest {
        val store = VariantStore()
        val variants = listOf(enhanced, topLeft, rotated)
        val scanned = mutableListOf<String>()
        val fallbacks = ScanFallbacks(
            barcodeDetector = { image ->
                scanned += image.path
                if (image.path == "variant1") listOf(ScannedCode(CodeFormat.EAN_13, "123", NormalizedRect(0.5f, 0.5f, 1f, 1f))).right()
                else emptyList<ScannedCode>().right()
            },
            imageProcessor = IndexingProcessor(variants),
            imageStore = store,
            variants = variants,
        )

        val found = fallbacks.scan(photo, photoAspectRatio = 1f)

        assertEquals(listOf(ScannedCode(CodeFormat.EAN_13, "123", NormalizedRect(0.25f, 0.25f, 0.5f, 0.5f))), found)
        assertEquals(listOf("variant0", "variant1"), scanned)
        assertEquals(store.saved, store.deleted) // temporary renders are cleaned up
    }

    @Test
    fun failingVariantsCountAsMisses() = runTest {
        val store = VariantStore()
        val processor = object : ImageProcessor by FakeImageProcessor() {
            override suspend fun renderForScan(image: StoredImage, variant: ScanVariant) =
                if (variant == enhanced) AnalysisError(AnalysisFailure.IMAGE_UNREADABLE).left()
                else "ok".encodeToByteArray().right()
        }
        val fallbacks = ScanFallbacks(
            barcodeDetector = { AnalysisError(AnalysisFailure.DETECTION_FAILED).left() },
            imageProcessor = processor,
            imageStore = store,
            variants = listOf(enhanced, topLeft),
        )

        assertEquals(emptyList(), fallbacks.scan(photo, 1f))
        assertEquals(listOf("ok"), store.deleted)
    }

    @Test
    fun givesUpWhenTheBudgetRunsOut() = runTest {
        val store = VariantStore()
        val variants = listOf(enhanced, topLeft)
        val scanned = mutableListOf<String>()
        val fallbacks = ScanFallbacks(
            barcodeDetector = { image ->
                scanned += image.path
                delay(4.seconds)
                listOf(ScannedCode(CodeFormat.QR, "late")).right()
            },
            imageProcessor = IndexingProcessor(variants),
            imageStore = store,
            variants = variants,
            budget = 3.seconds,
        )

        assertEquals(emptyList(), fallbacks.scan(photo, 1f))
        assertEquals(listOf("variant0"), scanned)
        assertEquals(listOf("variant0"), store.deleted)
    }
}
