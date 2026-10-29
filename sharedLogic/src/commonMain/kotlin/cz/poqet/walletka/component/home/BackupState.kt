package cz.poqet.walletka.component.home

/** Exporting the library to a backup file, or importing one ([HomeComponent.Model.backup]). */
sealed interface BackupState {
    data object Idle : BackupState

    data object InProgress : BackupState

    /**
     * The backup is written to [path]; the UI hands it to the system (save dialog / share sheet), suggesting
     * [fileName], then calls [HomeComponent.onExportFinished].
     */
    data class ExportReady(val path: String, val fileName: String, val cardCount: Int) : BackupState

    data class Imported(val added: Int, val updated: Int, val skipped: Int) : BackupState

    /** [operation] failed; [detail] is a technical reason (English, for diagnostics), the UI words the message. */
    data class Failed(val operation: BackupOperation, val detail: String?) : BackupState
}

enum class BackupOperation {
    EXPORT,
    IMPORT,
}
