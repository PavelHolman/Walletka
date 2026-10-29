package cz.poqet.walletka.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.home.HomeComponent
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import cz.poqet.walletka.ui.ImageSourceActions
import cz.poqet.walletka.ui.rememberStoredImage
import cz.poqet.walletka.ui.toColor
import cz.poqet.walletka.ui.rememberImageSourceActions
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.outlined.DragHandle
import cz.poqet.walletka.ui.rememberBackupFileActions
import cz.poqet.walletka.ui.localizedName
import cz.poqet.walletka.ui.resources.*
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@Composable
fun HomeContent(component: HomeComponent) {
    val model by component.model.collectAsState()
    val actions = rememberImageSourceActions(onImage = component::onImagePicked)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val paste = { if (!actions.pasteImage()) scope.launch { snackbar.showSnackbar(getString(Res.string.home_no_clipboard_image)) } }
    val backupFiles = rememberBackupFileActions(onExported = component::onExportFinished, onImportFile = component::onImportFileSelected)
    BackupEffects(model.backup, backupFiles, snackbar, onDismiss = component::onBackupMessageDismissed)

    if (model.cards.isNotEmpty()) {
        CardLibrary(
            model.visibleCards,
            actions,
            onPaste = { paste() },
            onCardClick = component::onCardClicked,
            onCardDelete = component::onCardDeleteClicked,
            snackbar = snackbar,
            onEnterManually = component::onEnterManuallyClicked,
            menu = { LibraryMenu(onExport = component::onExportClicked, onImport = backupFiles.pickImport) },
            search = if (model.isSearchAvailable) ({ LibrarySearchField(model.searchQuery, component::onSearchQueryChanged) }) else null,
            onMove = component::onCardMoved.takeIf { model.canReorder },
            canMove = model.canReorder,
            query = model.searchQuery,
        )
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The app icon, as on the launcher.
            Image(
                painterResource(Res.drawable.brand_mark),
                contentDescription = null,
                modifier = Modifier.size(112.dp).clip(RoundedCornerShape(28.dp)),
            )
            Spacer(Modifier.height(24.dp))
            Text("Walletka", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.home_intro),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = actions.takePhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.action_take_photo))
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = actions.pickPhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoLibrary, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.action_choose_photo))
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { paste() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ContentPaste, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.action_paste_image))
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = component::onEnterManuallyClicked, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Keyboard, null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(Res.string.action_enter_manually))
            }
            Spacer(Modifier.height(20.dp))
            Hint(Icons.Outlined.Share, stringResource(Res.string.home_share_tip))
            TextButton(onClick = backupFiles.pickImport) { Text(stringResource(Res.string.home_restore)) }
            if (model.isWalletAvailable == false) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(Res.string.wallet_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun Hint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardLibrary(
    cards: List<HomeComponent.CardSummary>,
    actions: ImageSourceActions,
    onPaste: () -> Unit,
    onCardClick: (String) -> Unit,
    onCardDelete: (String) -> Unit,
    snackbar: SnackbarHostState,
    onEnterManually: () -> Unit,
    menu: @Composable () -> Unit = {},
    search: (@Composable () -> Unit)? = null,
    /** Reorders [cards] (from, to); `null` while they can't be reordered. */
    onMove: ((Int, Int) -> Unit)? = null,
    /** Whether the order can be changed at all (screen readers get move actions instead of dragging). */
    canMove: Boolean = false,
    /** The search text, to say when nothing matches it. */
    query: String = "",
) {
    var isAddMenuOpen by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<HomeComponent.CardSummary?>(null) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderState(listState, cards.map { it.id }) { from, to -> onMove?.invoke(from, to) }
    // While dragging, the list shows the order under the finger.
    val shown = reorder.draggedKey?.let { reorder.order.mapNotNull { id -> cards.firstOrNull { it.id == id } } } ?: cards

    Scaffold(
        topBar = { TopAppBar(title = { Text("Walletka") }, actions = { menu() }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { isAddMenuOpen = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(stringResource(Res.string.action_add_card)) },
                )
                DropdownMenu(expanded = isAddMenuOpen, onDismissRequest = { isAddMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_take_photo)) },
                        leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                        onClick = { isAddMenuOpen = false; actions.takePhoto() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_choose_photo)) },
                        leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                        onClick = { isAddMenuOpen = false; actions.pickPhoto() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_paste_image)) },
                        leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                        onClick = { isAddMenuOpen = false; onPaste() },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.action_enter_manually)) },
                        leadingIcon = { Icon(Icons.Outlined.Keyboard, null) },
                        onClick = { isAddMenuOpen = false; onEnterManually() },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // Room for the floating button.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (search != null) item(key = "search") { search() }
            if (shown.isEmpty() && query.isNotBlank()) item(key = "no-results") {
                Text(
                    stringResource(Res.string.home_search_empty, query.trim()),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                )
            }
            items(shown, key = { it.id }) { card ->
                val index = cards.indexOf(card)
                CardTile(
                    card,
                    onClick = { onCardClick(card.id) },
                    onDelete = { pendingDeletion = card },
                    onMoveUp = if (canMove && onMove != null && index > 0) ({ onMove(index, index - 1) }) else null,
                    onMoveDown = if (canMove && onMove != null && index in 0 until cards.lastIndex) ({ onMove(index, index + 1) }) else null,
                    modifier = Modifier.reorderableItem(this, reorder, card.id),
                    dragHandle = if (onMove != null && cards.size > 1) Modifier.reorderHandle(reorder, card.id) else null,
                )
            }
        }
    }

    pendingDeletion?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text(stringResource(Res.string.delete_card_title, card.name)) },
            text = { Text(stringResource(Res.string.home_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { pendingDeletion = null; onCardDelete(card.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }
}

/** A library card drawn like a small wallet pass. */
@Composable
private fun CardTile(
    card: HomeComponent.CardSummary,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null,
    /** Shown instead of the chevron when the card can be dragged; the modifier detects the drag. */
    dragHandle: Modifier? = null,
) {
    val content = card.color.contentColor.toColor()
    val banner = rememberStoredImage(card.banner?.path, maxSize = 600)
    var isMenuOpen by remember { mutableStateOf(false) }
    val editLabel = stringResource(Res.string.action_edit)
    val deleteLabel = stringResource(Res.string.action_delete)
    val moveUpLabel = stringResource(Res.string.action_move_up)
    val moveDownLabel = stringResource(Res.string.action_move_down)
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(card.color.toColor())
                .combinedClickable(
                    onClick = onClick,
                    onClickLabel = editLabel,
                    onLongClick = { isMenuOpen = true },
                    onLongClickLabel = stringResource(Res.string.action_more_actions),
                )
                // One element for screen readers: name, code type and value, wallet state; actions instead of the menu
                // and the drag handle.
                .semantics(mergeDescendants = true) {
                    customActions = buildList {
                        add(CustomAccessibilityAction(editLabel) { onClick(); true })
                        add(CustomAccessibilityAction(deleteLabel) { onDelete(); true })
                        onMoveUp?.let { add(CustomAccessibilityAction(moveUpLabel) { it(); true }) }
                        onMoveDown?.let { add(CustomAccessibilityAction(moveDownLabel) { it(); true }) }
                    }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${card.codeFormat.localizedName()} · ${card.codeValue}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (card.isInWallet == false) NotInWalletBadge(content)
            }
            if (banner != null) {
                Image(
                    banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 96.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)),
                )
            }
            if (dragHandle != null) {
                // Dragging is for touch; screen readers use the move actions above.
                Icon(
                    Icons.Outlined.DragHandle,
                    contentDescription = null,
                    tint = content.copy(alpha = 0.7f),
                    modifier = dragHandle.size(48.dp).padding(12.dp).clearAndSetSemantics {},
                )
            } else {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = content.copy(alpha = 0.7f))
            }
        }
        // Long-press for more actions, as elsewhere on Android.
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            DropdownMenuItem(text = { Text(editLabel) }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { isMenuOpen = false; onClick() })
            DropdownMenuItem(
                text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { isMenuOpen = false; onDelete() },
            )
        }
    }
}
