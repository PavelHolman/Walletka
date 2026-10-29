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

@Composable
fun HomeContent(component: HomeComponent) {
    val model by component.model.collectAsState()
    val actions = rememberImageSourceActions(onImage = component::onImagePicked)
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val paste = { if (!actions.pasteImage()) scope.launch { snackbar.showSnackbar("There's no image in the clipboard") } }

    if (model.cards.isNotEmpty()) {
        CardLibrary(
            model.cards,
            actions,
            onPaste = { paste() },
            onCardClick = component::onCardClicked,
            onCardDelete = component::onCardDeleteClicked,
            snackbar = snackbar,
            onEnterManually = component::onEnterManuallyClicked,
        )
        return
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).safeDrawingPadding().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(112.dp)
                    .background(
                        Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary)),
                        RoundedCornerShape(28.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.QrCodeScanner, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }
            Spacer(Modifier.height(24.dp))
            Text("Walletka", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Snap or share a photo of any loyalty card, ticket or membership, and Walletka turns it into a Google Wallet card.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))
            Button(onClick = actions.takePhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoCamera, null)
                Spacer(Modifier.size(8.dp))
                Text("Take photo")
            }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = actions.pickPhoto, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.PhotoLibrary, null)
                Spacer(Modifier.size(8.dp))
                Text("Choose photo")
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { paste() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Outlined.ContentPaste, null)
                Spacer(Modifier.size(8.dp))
                Text("Paste image")
            }
            Spacer(Modifier.height(4.dp))
            TextButton(onClick = component::onEnterManuallyClicked, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Keyboard, null)
                Spacer(Modifier.size(8.dp))
                Text("Enter manually")
            }
            Spacer(Modifier.height(20.dp))
            Hint(Icons.Outlined.Share, "Tip: use Share → Add to Walletka from Photos or any app")
            if (model.isWalletAvailable == false) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Google Wallet isn't available on this device.",
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
) {
    var isAddMenuOpen by remember { mutableStateOf(false) }
    var pendingDeletion by remember { mutableStateOf<HomeComponent.CardSummary?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Walletka") }) },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            Box {
                ExtendedFloatingActionButton(
                    onClick = { isAddMenuOpen = true },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text("Add card") },
                )
                DropdownMenu(expanded = isAddMenuOpen, onDismissRequest = { isAddMenuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Take photo") },
                        leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                        onClick = { isAddMenuOpen = false; actions.takePhoto() },
                    )
                    DropdownMenuItem(
                        text = { Text("Choose photo") },
                        leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                        onClick = { isAddMenuOpen = false; actions.pickPhoto() },
                    )
                    DropdownMenuItem(
                        text = { Text("Paste image") },
                        leadingIcon = { Icon(Icons.Outlined.ContentPaste, null) },
                        onClick = { isAddMenuOpen = false; onPaste() },
                    )
                    DropdownMenuItem(
                        text = { Text("Enter manually") },
                        leadingIcon = { Icon(Icons.Outlined.Keyboard, null) },
                        onClick = { isAddMenuOpen = false; onEnterManually() },
                    )
                }
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                // Room for the floating button.
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(cards, key = { it.id }) { card ->
                CardTile(card, onClick = { onCardClick(card.id) }, onDelete = { pendingDeletion = card })
            }
        }
    }

    pendingDeletion?.let { card ->
        AlertDialog(
            onDismissRequest = { pendingDeletion = null },
            title = { Text("Delete “${card.name}”?") },
            text = { Text("The card is removed from Walletka. Remove it in Google Wallet too if you no longer need it.") },
            confirmButton = {
                TextButton(
                    onClick = { pendingDeletion = null; onCardDelete(card.id) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { pendingDeletion = null }) { Text("Cancel") } },
        )
    }
}

/** A library card drawn like a small wallet pass. */
@Composable
private fun CardTile(card: HomeComponent.CardSummary, onClick: () -> Unit, onDelete: () -> Unit) {
    val content = card.color.contentColor.toColor()
    val banner = rememberStoredImage(card.banner?.path, maxSize = 600)
    var isMenuOpen by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(card.color.toColor())
                .combinedClickable(onClick = onClick, onLongClick = { isMenuOpen = true }, onLongClickLabel = "More actions")
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(card.name, style = MaterialTheme.typography.titleMedium, color = content, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${card.codeFormat.displayName} · ${card.codeValue}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = content.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (banner != null) {
                Image(
                    banner,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(width = 96.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)),
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = content.copy(alpha = 0.7f))
        }
        // Long-press for more actions, as elsewhere on Android.
        DropdownMenu(expanded = isMenuOpen, onDismissRequest = { isMenuOpen = false }) {
            DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Outlined.Edit, null) }, onClick = { isMenuOpen = false; onClick() })
            DropdownMenuItem(
                text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { isMenuOpen = false; onDelete() },
            )
        }
    }
}
