package cz.poqet.walletka.ui

import androidx.compose.runtime.Composable

/** Platform file dialogs for library backups (the Storage Access Framework on Android). */
class BackupFileActions(
    /** Lets the user save the exported file at [path], suggesting [fileName]. */
    val export: (path: String, fileName: String) -> Unit,
    /** Lets the user pick a backup to import. */
    val pickImport: () -> Unit,
)

/**
 * [onExported] runs once the exported file is saved or the dialog cancelled; [onImportFile] gets a local copy of the
 * picked backup.
 */
@Composable
expect fun rememberBackupFileActions(onExported: () -> Unit, onImportFile: (path: String) -> Unit): BackupFileActions
