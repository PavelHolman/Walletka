package cz.poqet.walletka.platform

import android.content.Context
import android.graphics.BitmapFactory
import androidx.core.net.toUri
import arrow.core.Either
import arrow.core.raise.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.StoredImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** Stores images in the app's cache; [import] accepts `content://`, `file://` URIs and plain paths. */
internal class AndroidImageStore(private val context: Context) : ImageStore {

    private val directory: File get() = File(context.cacheDir, "images").apply { mkdirs() }

    override suspend fun import(source: String): Either<AnalysisError, StoredImage> = withContext(Dispatchers.IO) {
        either {
            val target = File(directory, "${UUID.randomUUID()}.img")
            catch({
                val uri = if (source.startsWith("/")) File(source).toUri() else source.toUri()
                val input = checkNotNull(context.contentResolver.openInputStream(uri)) { "Cannot open $source" }
                input.use { stream -> target.outputStream().use { stream.copyTo(it) } }
            }) { e: Exception ->
                target.delete()
                raise(AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, e.message))
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(target.path, bounds)
            ensure(bounds.outWidth > 0 && bounds.outHeight > 0) {
                target.delete()
                AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, "Not an image")
            }
            StoredImage(target.path)
        }
    }

    override suspend fun save(png: ByteArray): Either<AnalysisError, StoredImage> = withContext(Dispatchers.IO) {
        Either.catch {
            val file = File(directory, "${UUID.randomUUID()}.png")
            file.writeBytes(png)
            StoredImage(file.path)
        }.mapLeft { AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, it.message) }
    }

    override suspend fun delete(image: StoredImage) {
        withContext(Dispatchers.IO) { File(image.path).delete() }
    }
}
