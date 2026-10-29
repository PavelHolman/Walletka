package cz.poqet.walletka.ui.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.review.CardReviewComponent.SyncStatus

/** Where a library card stands with Google Wallet; edits are pushed automatically (see `SyncStatusView` on iOS). */
@Composable
internal fun SyncStatusRow(status: SyncStatus, error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            SyncStatus.SYNCED -> {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text("Up to date in Google Wallet", color = color, style = MaterialTheme.typography.bodyMedium)
            }
            SyncStatus.PENDING -> {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = color)
                Text("Changes will sync to Google Wallet", color = color, style = MaterialTheme.typography.bodyMedium)
            }
            SyncStatus.SYNCING -> {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Syncing with Google Wallet…", color = color, style = MaterialTheme.typography.bodyMedium)
            }
            SyncStatus.FAILED -> {
                Icon(Icons.Outlined.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(
                    "Couldn't sync" + (error?.let { ": $it" } ?: ""),
                    Modifier.weight(1f, fill = false),
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                TextButton(onClick = onRetry) { Text("Retry") }
            }
            // The add button is shown instead (the pass has to be saved again).
            SyncStatus.NOT_IN_WALLET -> Text("Not in Google Wallet anymore. Add it again.", color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
