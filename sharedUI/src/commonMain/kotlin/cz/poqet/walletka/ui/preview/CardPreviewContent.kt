package cz.poqet.walletka.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.preview.CardPreviewComponent
import cz.poqet.walletka.component.preview.CardPreviewComponent.PreviewModel
import cz.poqet.walletka.component.preview.CardPreviewComponent.WalletAction
import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.domain.WalletFailure
import cz.poqet.walletka.ui.KeepScreenBright
import cz.poqet.walletka.ui.formatLocalDate
import cz.poqet.walletka.ui.resources.*
import cz.poqet.walletka.ui.review.AddToWalletButton
import cz.poqet.walletka.ui.review.ButtonProgress
import cz.poqet.walletka.ui.review.CardPreview
import cz.poqet.walletka.ui.review.CardPreviewSize
import cz.poqet.walletka.ui.review.message
import org.jetbrains.compose.resources.stringResource

/** A library card shown big, at full brightness, to be scanned; its wallet pass is added or removed from here. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardPreviewContent(component: CardPreviewComponent) {
    val model by component.model.collectAsState()
    val showing = model as? PreviewModel.Showing
    var isMenuOpen by remember { mutableStateOf(false) }
    var isConfirmingDelete by remember { mutableStateOf(false) }
    var isConfirmingRemove by remember { mutableStateOf(false) }
    KeepScreenBright()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = component::onCloseClicked) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    if (showing != null) {
                        IconButton(onClick = component::onEditClicked) {
                            Icon(Icons.Outlined.Edit, contentDescription = stringResource(Res.string.action_edit))
                        }
                        Box {
                            IconButton(onClick = { isMenuOpen = true }) {
                                Icon(Icons.Outlined.MoreVert, contentDescription = stringResource(Res.string.action_more_options))
                            }
                            DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.action_delete_card), color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = { isMenuOpen = false; isConfirmingDelete = true },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (showing != null) Showing(showing, onAdd = component::onAddToWalletClicked, onRemove = { isConfirmingRemove = true })
        }
    }

    if (showing == null) return
    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text(stringResource(Res.string.delete_card_title, showing.form.name)) },
            text = { Text(stringResource(Res.string.home_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { isConfirmingDelete = false; component.onDeleteClicked() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    if (isConfirmingRemove) {
        AlertDialog(
            onDismissRequest = { isConfirmingRemove = false },
            title = { Text(stringResource(Res.string.card_remove_title)) },
            text = { Text(stringResource(Res.string.card_remove_message)) },
            confirmButton = {
                TextButton(onClick = { isConfirmingRemove = false; component.onRemoveFromWalletClicked() }) {
                    Text(stringResource(Res.string.action_remove))
                }
            },
            dismissButton = { TextButton(onClick = { isConfirmingRemove = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
    when (val action = showing.walletAction) {
        is WalletAction.Failed -> if (action.failure != WalletFailure.CANCELLED) {
            AlertDialog(
                onDismissRequest = component::onWalletMessageDismissed,
                icon = { Icon(Icons.Outlined.ErrorOutline, null) },
                title = { Text(stringResource(Res.string.submission_failed_add)) },
                text = { Text(action.failure.message() + (action.detail?.let { "\n\n$it" } ?: "")) },
                confirmButton = { TextButton(onClick = component::onWalletMessageDismissed) { Text(stringResource(Res.string.action_ok)) } },
            )
        }

        WalletAction.RemoveUnsupported -> AlertDialog(
            onDismissRequest = component::onWalletMessageDismissed,
            title = { Text(stringResource(Res.string.card_remove_unsupported_title)) },
            text = { Text(stringResource(Res.string.card_remove_unsupported_message)) },
            confirmButton = { TextButton(onClick = component::onWalletMessageDismissed) { Text(stringResource(Res.string.action_ok)) } },
        )

        else -> Unit
    }
}

@Composable
private fun Showing(model: PreviewModel.Showing, onAdd: () -> Unit, onRemove: () -> Unit) {
    val action = model.walletAction
    val busy = action == WalletAction.Adding || action == WalletAction.Removing
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            CardPreview(model.form, CardPreviewSize.LARGE, Modifier.widthIn(max = 480.dp))
            Details(model.form.details, Modifier.widthIn(max = 480.dp))
        }

        Surface(tonalElevation = 3.dp) {
            Column(
                Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (model.isInWallet) {
                    Row(
                        Modifier.heightIn(min = 40.dp).semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(stringResource(Res.string.card_in_wallet), style = MaterialTheme.typography.bodyMedium)
                    }
                    TextButton(onClick = onRemove, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
                        if (action == WalletAction.Removing) {
                            ButtonProgress(stringResource(Res.string.card_removing), MaterialTheme.colorScheme.primary)
                        } else {
                            Text(stringResource(Res.string.card_remove_from_wallet))
                        }
                    }
                } else {
                    AddToWalletButton(onClick = onAdd, enabled = !busy, busy = action == WalletAction.Adding)
                }
            }
        }
    }
}

/** The pass details that are filled in, label above value. */
@Composable
private fun Details(details: CardDetails, modifier: Modifier = Modifier) {
    val rows = listOfNotNull(
        details.memberNumber.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.details_card_number) to it.trim() },
        details.memberName.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.details_member_name) to it.trim() },
        details.expiryEpochDay?.let { stringResource(Res.string.details_expiry) to formatLocalDate(it) },
        details.notes.takeIf { it.isNotBlank() }?.let { stringResource(Res.string.details_notes) to it.trim() },
    )
    if (rows.isEmpty()) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        rows.forEach { (label, value) ->
            // Read as one element: "Card number, 1234 5678".
            Column(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
