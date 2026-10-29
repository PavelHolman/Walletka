package cz.poqet.walletka.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import cz.poqet.walletka.domain.CodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.EncodeHintType
import com.google.zxing.BarcodeFormat
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

@Composable
actual fun rememberCodeImage(format: CodeFormat, value: String): ImageBitmap? {
    val image by produceState<ImageBitmap?>(null, format, value) {
        this.value = withContext(Dispatchers.Default) { codeBitmap(format, value)?.asImageBitmap() }
    }
    return image
}

/** One pixel per module; the UI scales it up without smoothing. */
private fun codeBitmap(format: CodeFormat, value: String): android.graphics.Bitmap? {
    if (value.isBlank()) return null
    val barcodeFormat = when (format) {
        CodeFormat.QR -> BarcodeFormat.QR_CODE
        CodeFormat.AZTEC -> BarcodeFormat.AZTEC
        CodeFormat.PDF417 -> BarcodeFormat.PDF_417
        CodeFormat.DATA_MATRIX -> BarcodeFormat.DATA_MATRIX
        CodeFormat.CODE_128 -> BarcodeFormat.CODE_128
        CodeFormat.CODE_39 -> BarcodeFormat.CODE_39
        CodeFormat.CODE_93 -> BarcodeFormat.CODE_93
        CodeFormat.CODABAR -> BarcodeFormat.CODABAR
        CodeFormat.EAN_13 -> BarcodeFormat.EAN_13
        CodeFormat.EAN_8 -> BarcodeFormat.EAN_8
        CodeFormat.UPC_A -> BarcodeFormat.UPC_A
        CodeFormat.UPC_E -> BarcodeFormat.UPC_E
        CodeFormat.ITF -> BarcodeFormat.ITF
    }
    val matrix = runCatching {
        MultiFormatWriter().encode(value, barcodeFormat, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
    }.getOrNull() ?: return null
    val pixels = IntArray(matrix.width * matrix.height) { i ->
        if (matrix[i % matrix.width, i / matrix.width]) android.graphics.Color.BLACK else android.graphics.Color.WHITE
    }
    return android.graphics.Bitmap.createBitmap(pixels, matrix.width, matrix.height, android.graphics.Bitmap.Config.ARGB_8888)
}

actual fun formatLocalDate(epochDay: Long): String =
    java.time.LocalDate.ofEpochDay(epochDay).format(java.time.format.DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.MEDIUM))
