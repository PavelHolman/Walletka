package cz.poqet.walletka.platform

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import cz.poqet.walletka.domain.AnalysisError
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.StoredImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSURL
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import platform.Foundation.writeToFile
import platform.UIKit.UIImage

/** Stores images in the app's caches directory; [import] accepts file paths and `file://` URLs. */
internal class IosImageStore : ImageStore {

    private val fileManager = NSFileManager.defaultManager

    private val directory: String by lazy {
        val caches = NSSearchPathForDirectoriesInDomains(NSCachesDirectory, NSUserDomainMask, true).first() as String
        "$caches/images".also { fileManager.createDirectoryAtPath(it, withIntermediateDirectories = true, attributes = null, error = null) }
    }

    override suspend fun import(source: String): Either<AnalysisError, StoredImage> = withContext(Dispatchers.IO) {
        either {
            val path = if (source.startsWith("file:")) NSURL.URLWithString(source)?.path else source
            ensureNotNull(path) { AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, "Invalid source $source") }
            val target = "$directory/${NSUUID().UUIDString}.img"
            ensure(fileManager.copyItemAtPath(path, toPath = target, error = null)) {
                AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, "Cannot copy $source")
            }
            ensure(UIImage.imageWithContentsOfFile(target) != null) {
                fileManager.removeItemAtPath(target, error = null)
                AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, "Not an image")
            }
            StoredImage(target)
        }
    }

    override suspend fun save(png: ByteArray): Either<AnalysisError, StoredImage> = withContext(Dispatchers.IO) {
        either {
            val target = "$directory/${NSUUID().UUIDString}.png"
            ensure(png.toNSData().writeToFile(target, atomically = true)) {
                AnalysisError(AnalysisFailure.IMAGE_UNREADABLE, "Cannot write $target")
            }
            StoredImage(target)
        }
    }

    override suspend fun delete(image: StoredImage) {
        withContext(Dispatchers.IO) { fileManager.removeItemAtPath(image.path, error = null) }
    }
}
