package cz.poqet.walletka.ui.review

import androidx.compose.foundation.BorderStroke
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.review.CardReviewComponent
import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.component.review.CardReviewComponent.LoadingStage
import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.component.review.CardReviewComponent.Submission
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.WalletFailure
import cz.poqet.walletka.ui.rememberStoredImage
import cz.poqet.walletka.ui.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardReviewContent(component: CardReviewComponent) {
    val model by component.model.collectAsState()
    var isConfirmingDelete by remember { mutableStateOf(false) }
    (model as? Model.Editing)?.form?.takeIf { isConfirmingDelete }?.let { form ->
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text("Delete “${form.name}”?") },
            text = { Text("The card is removed from Walletka. Google Wallet doesn't let apps remove passes, so remove it in Google Wallet too if you no longer need it.") },
            confirmButton = {
                TextButton(
                    onClick = { isConfirmingDelete = false; component.onDeleteClicked() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text("Cancel") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if ((model as? Model.Editing)?.form?.isLibraryCard == true) "Edit card" else "New card") },
                navigationIcon = {
                    IconButton(onClick = component::onCloseClicked) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val editing = model as? Model.Editing
                    if (editing?.form?.isLibraryCard == true && editing.submission !is Submission.Added) {
                        IconButton(onClick = { isConfirmingDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete card", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = model) {
                is Model.Loading -> Loading(state.stage, state.photo?.path)
                is Model.Failed -> Failed(state.failure, onRetry = component::onRetryClicked, onClose = component::onCloseClicked)
                is Model.Editing -> when (val submission = state.submission) {
                    is Submission.Added -> CardAdded(state.form, submission.replacedExisting, onDone = component::onCloseClicked)
                    else -> Editing(state.form, submission, state.hasChanges, state.colorPreview, component)
                }
            }
        }
    }
}

@Composable
private fun Loading(stage: LoadingStage, photoPath: String?) {
    val photo = rememberStoredImage(photoPath, maxSize = 1200)
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (photo != null) {
            // The photo being scanned, with a sweeping scan line.
            val sweep by rememberInfiniteTransition(label = "scan").animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
                label = "sweep",
            )
            val accent = MaterialTheme.colorScheme.primary
            Image(
                photo,
                contentDescription = null,
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .drawWithContent {
                        drawContent()
                        val band = 64.dp.toPx()
                        val top = sweep * (size.height - band)
                        drawRect(
                            Brush.verticalGradient(listOf(Color.Transparent, accent.copy(alpha = 0.55f), Color.Transparent), top, top + band),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, band),
                        )
                    },
            )
            Spacer(Modifier.height(24.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                when (stage) {
                    LoadingStage.IMPORTING -> "Reading the image…"
                    LoadingStage.ANALYZING -> "Looking for codes…"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Failed(failure: AnalysisFailure, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(
            when (failure) {
                AnalysisFailure.IMAGE_UNREADABLE -> "This doesn't look like an image Walletka can read."
                AnalysisFailure.NO_CODE_FOUND -> "No QR code or barcode found. Try a sharper photo with the whole code visible."
                AnalysisFailure.DETECTION_FAILED -> "Something went wrong while scanning the photo."
            },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onClose) { Text("Choose another") }
            if (failure == AnalysisFailure.DETECTION_FAILED) Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editing(
    form: CardForm,
    submission: Submission,
    hasChanges: Boolean,
    colorPreview: CardColor?,
    component: CardReviewComponent,
) {
    val busy = submission == Submission.InProgress
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress) focusManager.clearFocus()
    }
    var colorDialog by remember { mutableStateOf<ColorDialog?>(null) }
    var isNameFocused by remember { mutableStateOf(false) }
    when (colorDialog) {
        ColorDialog.PHOTO -> form.photo?.let { photo ->
            PhotoColorPickerDialog(
                photoPath = photo.path,
                preview = colorPreview,
                onPick = component::onColorPreviewRequested,
                onConfirm = { component.onColorPreviewConfirmed(); colorDialog = null },
                onDismiss = { component.onColorPreviewDismissed(); colorDialog = null },
            )
        }
        ColorDialog.CUSTOM -> CustomColorDialog(
            initial = form.customColor ?: form.selectedColor,
            onPick = component::onCustomColorSelected,
            onDismiss = { colorDialog = null },
        )
        null -> Unit
    }
    var isEditingBanner by remember { mutableStateOf(false) }
    if (isEditingBanner) {
        BannerEditorDialog(
            form = form,
            onDone = { region ->
                component.onBannerRegionChanged(region)
                if (!form.isBannerEnabled) component.onBannerToggled(true)
                isEditingBanner = false
            },
            onDismiss = { isEditingBanner = false },
        )
    }
    Column(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .weight(1f)
            .fillMaxWidth()
            // Taps that no control consumes (empty space, labels) close the keyboard.
            .pointerInput(Unit) { detectTapGestures(onTap = { focusManager.clearFocus() }) }
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CardPreview(form, compact = true)

        Section("Name") {
            OutlinedTextField(
                value = form.name,
                onValueChange = component::onNameChanged,
                modifier = Modifier.fillMaxWidth().onFocusChanged { isNameFocused = it.isFocused },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                placeholder = { Text("e.g. Coffee Club") },
                trailingIcon = {
                    if (isNameFocused && form.name.isNotEmpty()) {
                        IconButton(onClick = { component.onNameChanged("") }) { Icon(Icons.Outlined.Cancel, contentDescription = "Clear name") }
                    }
                },
                isError = !form.isValid,
            )
        }

        Section(if (form.codes.size > 1) "Codes found (${form.codes.size})" else "Code") {
            if (form.codes.size > 1) form.codes.forEachIndexed { index, code ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .selectable(index == form.selectedCodeIndex, enabled = !busy, role = Role.RadioButton) {
                            component.onCodeSelected(index)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (form.codes.size > 1) RadioButton(selected = index == form.selectedCodeIndex, onClick = null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(code.format.displayName, style = MaterialTheme.typography.labelLarge)
                        Text(
                            code.value,
                            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (form.isCodeTyped) TypedCodeFormatPicker(form, enabled = !busy, onSelect = component::onCodeFormatSelected)
            OutlinedTextField(
                value = form.selectedCode.value,
                onValueChange = component::onCodeValueChanged,
                label = { Text(form.selectedCode.format.displayName) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                isError = form.selectedCode.value.isBlank(),
            )
            if (form.walletCodeFormat != form.selectedCode.format) {
                Text(
                    "Google Wallet doesn't support ${form.selectedCode.format.displayName}; " +
                        "it will be shown as ${form.walletCodeFormat.displayName} with the same content.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section("Colour") {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.colors.forEachIndexed { index, color ->
                    val selected = index == form.selectedColorIndex
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(color.toColor())
                            .border(
                                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                CircleShape,
                            )
                            .clickable(enabled = !busy) {
                                focusManager.clearFocus()
                                component.onColorSelected(index)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Outlined.Check, null, tint = color.contentColor.toColor())
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (form.hasPhoto) OutlinedButton(onClick = { focusManager.clearFocus(); colorDialog = ColorDialog.PHOTO }, enabled = !busy) {
                    Icon(Icons.Outlined.Colorize, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Pick from photo")
                }
                OutlinedButton(onClick = { focusManager.clearFocus(); colorDialog = ColorDialog.CUSTOM }, enabled = !busy) {
                    Icon(Icons.Outlined.Palette, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Custom")
                }
            }
        }

        if (form.banner != null) {
            Section("Banner") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Banner from photo", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "A strip from your photo, shown as the card's header image.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = form.isBannerEnabled, onCheckedChange = component::onBannerToggled, enabled = !busy)
                }
                if (form.isBannerEnabled) {
                    val thumbnail = rememberStoredImage(form.banner?.path, maxSize = 600)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !busy) { focusManager.clearFocus(); isEditingBanner = true }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(width = 96.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (thumbnail != null) Image(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text("Adjust banner", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
    }

    // The primary action stays reachable at the bottom of the screen.
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Button(
                onClick = {
                    focusManager.clearFocus()
                    component.onAddToWalletClicked()
                },
                enabled = form.isValid && !busy && hasChanges,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Icon(Icons.Outlined.Wallet, null)
                    Spacer(Modifier.size(8.dp))
                    Text(if (form.isLibraryCard) "Save to Google Wallet" else "Add to Google Wallet")
                }
            }
            if (form.isLibraryCard && !hasChanges) {
                Text(
                    "Edit the card to save it to Google Wallet again.",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    }

    when (submission) {
        is Submission.Failed -> if (submission.failure != WalletFailure.CANCELLED) {
            AlertDialog(
                onDismissRequest = component::onSubmissionErrorDismissed,
                icon = { Icon(Icons.Outlined.ErrorOutline, null) },
                title = { Text(if (form.isLibraryCard) "Couldn't save the card" else "Couldn't add the card") },
                text = { Text(submission.failure.message() + (submission.detail?.let { "\n\n$it" } ?: "")) },
                confirmButton = { TextButton(onClick = component::onSubmissionErrorDismissed) { Text("OK") } },
            )
        }

        else -> Unit
    }
}

/** Shown once Google Wallet reports the pass as saved. */
@Composable
private fun CardAdded(form: CardForm, replacedExisting: Boolean, onDone: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(64.dp), tint = Color(0xFF2E7D32))
        Text(if (replacedExisting) "Updated in Google Wallet" else "Saved to Google Wallet", style = MaterialTheme.typography.headlineSmall)
        Text(
            "“${form.name}” is ready to scan from Google Wallet.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        CardPreview(form)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(26.dp)) {
            Text("Done")
        }
    }
}

@Composable
private fun CardPreview(form: CardForm, compact: Boolean = false) {
    val background = form.selectedColor.toColor()
    val content = form.selectedColor.contentColor.toColor()
    val banner = rememberStoredImage(form.banner?.path?.takeIf { form.isBannerEnabled })
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background),
    ) {
        Text(
            form.name.ifBlank { "Card name" },
            modifier = Modifier.padding(horizontal = 20.dp, vertical = if (compact) 14.dp else 20.dp),
            color = content,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (banner != null) {
            Image(
                banner,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth().aspectRatio(375f / 123f),
                contentScale = ContentScale.Crop,
            )
        }
        Box(
            Modifier
                .padding(if (compact) 14.dp else 20.dp)
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(12.dp))
                .background(Color.White)
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(form.walletCodeFormat.displayName, color = Color.Black, style = MaterialTheme.typography.labelMedium)
                Text(
                    form.selectedCode.value,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}

private fun WalletFailure.message(): String = when (this) {
    WalletFailure.UNAVAILABLE -> "Google Wallet isn't available on this device."
    WalletFailure.NOT_CONFIGURED -> "This build has no Google Wallet issuer configured. Set walletka.googleIssuerId and walletka.googleIssuerEmail in local.properties."
    WalletFailure.CANCELLED -> "Cancelled."
    WalletFailure.UNSUPPORTED_CODE -> "This code type can't be stored in the wallet."
    WalletFailure.FAILED -> "The wallet rejected the card."
    WalletFailure.NOT_IN_WALLET -> "The card is no longer in Google Wallet."
}
