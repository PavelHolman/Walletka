package cz.poqet.walletka.ui.home

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

/** The overflow menu of the library: export and import. */
@Composable
internal fun LibraryMenu(onExport: () -> Unit, onImport: () -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { isOpen = true }) { Icon(Icons.Outlined.MoreVert, contentDescription = "More options") }
        DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
            DropdownMenuItem(
                text = { Text("Export library") },
                leadingIcon = { Icon(Icons.Outlined.Upload, null) },
                onClick = { isOpen = false; onExport() },
            )
            DropdownMenuItem(
                text = { Text("Import library") },
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
                    add("${backup.added} added")
                    if (backup.updated > 0) add("${backup.updated} updated")
                    if (backup.skipped > 0) add("${backup.skipped} already up to date")
                }
                // Dismissing restarts this effect, so only once the message is gone.
                snackbar.showSnackbar("Library imported: ${details.joinToString()}")
                currentOnDismiss()
            }
            is BackupState.Failed -> {
                snackbar.showSnackbar(backup.message)
                currentOnDismiss()
            }
            BackupState.Idle, BackupState.InProgress -> Unit
        }
    }
}

@Composable
internal fun LibrarySearchField(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("Search by name or code") },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Outlined.Close, contentDescription = "Clear search") }
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
        "Not in Google Wallet",
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .padding(top = 4.dp)
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}
