package cz.poqet.walletka.ui.home

import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.home.BackupState
import cz.poqet.walletka.ui.BackupFileActions
import cz.poqet.walletka.component.home.BackupOperation
import cz.poqet.walletka.ui.resources.*
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource

/** The overflow menu of the library: export and import. */
@Composable
internal fun LibraryMenu(onExport: () -> Unit, onImport: () -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(Res.string.action_more_options)) }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.library_export)) },
                leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                onClick = { isOpen = false; onExport() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(Res.string.library_import)) },
                leadingIcon = { Icon(Icons.Outlined.Download, null) },
                onClick = { isOpen = false; onImport() },
            )
        }
    }
}

/** Hands a finished export to the save dialog and reports imports and failures in the snackbar. */
@Composable
internal fun BackupEffects(backup: BackupState, files: BackupFileActions, snackbar: SnackbarHostState, onDismiss: () -> Unit) {
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(backup) {
        when (backup) {
            is BackupState.ExportReady -> files.export(backup.path, backup.fileName)
            is BackupState.Imported -> {
                val details = buildList {
                    add(getPluralString(Res.plurals.import_added, backup.added, backup.added))
                    if (backup.updated > 0) add(getPluralString(Res.plurals.import_updated, backup.updated, backup.updated))
                    if (backup.skipped > 0) add(getPluralString(Res.plurals.import_skipped, backup.skipped, backup.skipped))
                }
                // Dismissing restarts this effect, so only once the message is gone.
                snackbar.showSnackbar(getString(Res.string.library_imported, details.joinToString()))
                currentOnDismiss()
            }
            is BackupState.Failed -> {
                // The detail is a technical (English) reason, left out of the localized message.
                val message = when (backup.operation) {
                    BackupOperation.EXPORT -> Res.string.backup_export_failed
                    BackupOperation.IMPORT -> Res.string.backup_import_failed
                }
                snackbar.showSnackbar(getString(message))
                currentOnDismiss()
            }
            BackupState.Idle, BackupState.InProgress -> Unit
        }
    }
    // Exporting or importing a large library takes a moment; block the screen meanwhile, as iOS does.
    if (backup is BackupState.InProgress) {
        Dialog(onDismissRequest = {}, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
                CircularProgressIndicator(Modifier.padding(28.dp))
            }
        }
    }
}

@Composable
internal fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text(stringResource(Res.string.search_placeholder)) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = stringResource(Res.string.search_clear)) }
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
    )
}

/** Marks a card known not to be in Google Wallet on this phone (cards restored from a backup). */
@Composable
internal fun NotInWalletBadge(color: Color) {
    Text(
        stringResource(Res.string.not_in_wallet),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
