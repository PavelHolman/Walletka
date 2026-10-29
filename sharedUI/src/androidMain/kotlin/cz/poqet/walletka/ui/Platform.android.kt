package cz.poqet.walletka.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import android.graphics.ImageDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max

@Composable
actual fun rememberImageSourceActions(onImage: (source: String) -> Unit): ImageSourceActions {
    val context = LocalContext.current
    val currentOnImage by rememberUpdatedState(onImage)
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { currentOnImage(it.toString()) }
    }
    // The system camera writes the photo straight into our cache through the app's FileProvider.
    var pendingCapture by remember { mutableStateOf<File?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val file = pendingCapture
        pendingCapture = null
        if (saved && file != null && file.length() > 0) currentOnImage(file.path) else file?.delete()
    }
    return remember(context, picker, camera) {
        ImageSourceActions(
            pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            takePhoto = {
                val file = File(File(context.cacheDir, "camera").apply { mkdirs() }, "${System.currentTimeMillis()}.jpg")
                pendingCapture = file
                camera.launch(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file))
            },
            pasteImage = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = clipboard.primaryClip
                val isImage = clip?.description?.let { description ->
                    (0 until description.mimeTypeCount).any { description.getMimeType(it).startsWith("image/") }
                } == true
                val uri = clip?.takeIf { isImage && it.itemCount > 0 }?.getItemAt(0)?.uri
                uri?.let { currentOnImage(it.toString()) }
                uri != null
            },
        )
    }
}

@Composable
actual fun rememberStoredImage(path: String?, maxSize: Int): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                    val longer = max(info.size.width, info.size.height)
                    if (longer > maxSize) {
                        val scale = maxSize.toFloat() / longer
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    }
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}
