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
import cz.poqet.walletka.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

/** Where a library card stands with Google Wallet; edits are pushed automatically (see `SyncStatusView` on iOS). */
@Composable
internal fun SyncStatusRow(status: SyncStatus, error: String?, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        // Status changes are announced; the retry button stays its own element.
        modifier.fillMaxWidth().heightIn(min = 48.dp).semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            SyncStatus.SYNCED -> {
                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(Res.string.sync_up_to_date), color = color, style = MaterialTheme.typography.bodyMedium)
            }
            SyncStatus.PENDING -> {
                Icon(Icons.Outlined.Schedule, null, Modifier.size(18.dp), tint = color)
                Text(stringResource(Res.string.sync_pending), color = color, style = MaterialTheme.typography.bodyMedium)
            }
            SyncStatus.SYNCING -> {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(stringResource(Res.string.sync_syncing), color = color, style = MaterialTheme.typography.bodyMedium)
            }
            SyncStatus.FAILED -> {
                Icon(Icons.Outlined.Warning, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                Text(
                    error?.let { stringResource(Res.string.sync_failed_detail, it) } ?: stringResource(Res.string.sync_failed),
                    Modifier.weight(1f, fill = false),
                    color = color,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                )
                TextButton(onClick = onRetry) { Text(stringResource(Res.string.action_retry)) }
            }
            // The add button is shown instead (the pass has to be saved again).
            SyncStatus.NOT_IN_WALLET -> Text(stringResource(Res.string.sync_not_in_wallet), color = color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
