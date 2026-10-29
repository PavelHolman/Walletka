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
 * [onExported] runs once the dialog is done: `true` when the file was saved, `false` when writing it failed, `null`
 * when cancelled. [onImportFile] gets a local copy of the picked backup, [onImportFailed] runs when it can't be read.
 */
@Composable
expect fun rememberBackupFileActions(
    onExported: (saved: Boolean?) -> Unit,
    onImportFile: (path: String) -> Unit,
    onImportFailed: () -> Unit,
): BackupFileActions
