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
    return remember(context, picker) {
        ImageSourceActions(
            pickPhoto = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
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
actual fun rememberStoredImage(path: String?): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        if (path == null) return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(File(path))) { decoder, info, _ ->
                    val longer = max(info.size.width, info.size.height)
                    if (longer > MAX_DISPLAY_SIZE) {
                        val scale = MAX_DISPLAY_SIZE.toFloat() / longer
                        decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
                    }
                }.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

private const val MAX_DISPLAY_SIZE = 1600
