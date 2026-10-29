package cz.poqet.walletka.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
actual fun rememberBackupFileActions(onExported: () -> Unit, onImportFile: (path: String) -> Unit): BackupFileActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnExported by rememberUpdatedState(onExported)
    val currentOnImportFile by rememberUpdatedState(onImportFile)
    var pendingExport by rememberSaveable { mutableStateOf<String?>(null) }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(MIME_TYPE)) { uri: Uri? ->
        val path = pendingExport
        pendingExport = null
        scope.launch {
            if (uri != null && path != null) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use { output -> File(path).inputStream().use { it.copyTo(output) } }
                    }
                }
            }
            currentOnExported()
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val copy = withContext(Dispatchers.IO) {
                runCatching {
                    val file = File(context.cacheDir, "import.walletka")
                    context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                    file.path
                }.getOrNull()
            }
            copy?.let(currentOnImportFile)
        }
    }
    return remember(create, open) {
        BackupFileActions(
            export = { path, fileName ->
                pendingExport = path
                create.launch(fileName)
            },
            // Some file providers don't know the .walletka type, so any file can be picked; the import validates it.
            pickImport = { open.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        )
    }
}

private const val MIME_TYPE = "application/octet-stream"
