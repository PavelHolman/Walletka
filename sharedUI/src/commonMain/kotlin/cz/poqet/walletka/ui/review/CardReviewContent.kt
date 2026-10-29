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
import androidx.compose.foundation.selection.toggleable
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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.review.CardReviewComponent
import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.component.review.CardReviewComponent.LoadingStage
import cz.poqet.walletka.component.review.CardReviewComponent.Model
import cz.poqet.walletka.component.review.CardReviewComponent.Submission
import cz.poqet.walletka.component.review.CardReviewComponent.SyncStatus
import cz.poqet.walletka.domain.AnalysisFailure
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.domain.CodeFormat
import cz.poqet.walletka.domain.CodeIssue
import cz.poqet.walletka.domain.WalletFailure
import cz.poqet.walletka.ui.rememberCodeImage
import cz.poqet.walletka.ui.rememberStoredImage
import cz.poqet.walletka.ui.toColor
import cz.poqet.walletka.ui.displayHex
import cz.poqet.walletka.ui.localizedName
import cz.poqet.walletka.ui.spokenName
import cz.poqet.walletka.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardReviewContent(component: CardReviewComponent) {
    val model by component.model.collectAsState()
    var isConfirmingDelete by remember { mutableStateOf(false) }
    (model as? Model.Editing)?.form?.takeIf { isConfirmingDelete }?.let { form ->
        AlertDialog(
            onDismissRequest = { isConfirmingDelete = false },
            title = { Text(stringResource(Res.string.delete_card_title, form.name)) },
            text = { Text(stringResource(Res.string.review_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = { isConfirmingDelete = false; component.onDeleteClicked() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(Res.string.action_delete)) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingDelete = false }) { Text(stringResource(Res.string.action_cancel)) } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if ((model as? Model.Editing)?.form?.isLibraryCard == true) Res.string.review_title_edit else Res.string.review_title_new)) },
                navigationIcon = {
                    IconButton(onClick = component::onCloseClicked) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
                actions = {
                    val editing = model as? Model.Editing
                    if (editing?.form?.isLibraryCard == true && editing.submission !is Submission.Added) {
                        IconButton(onClick = { isConfirmingDelete = true }) {
                            Icon(Icons.Outlined.Delete, contentDescription = stringResource(Res.string.review_delete_card), tint = MaterialTheme.colorScheme.error)
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
                    else -> Column {
                        state.duplicateOf?.let { DuplicateCardBanner(it, onOpen = component::onOpenDuplicateClicked) }
                        Editing(state.form, submission, state.hasChanges, state.colorPreview, component, state.sync, state.syncError)
                    }
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
        Row(
            Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text(
                when (stage) {
                    LoadingStage.IMPORTING -> stringResource(Res.string.loading_importing)
                    LoadingStage.ANALYZING -> stringResource(Res.string.loading_analyzing)
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Failed(failure: AnalysisFailure, onRetry: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Text(
            when (failure) {
                AnalysisFailure.IMAGE_UNREADABLE -> stringResource(Res.string.failure_image_unreadable)
                AnalysisFailure.NO_CODE_FOUND -> stringResource(Res.string.failure_no_code)
                AnalysisFailure.DETECTION_FAILED -> stringResource(Res.string.failure_detection)
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            OutlinedButton(onClick = onClose) { Text(stringResource(Res.string.failure_choose_another)) }
            if (failure == AnalysisFailure.DETECTION_FAILED) Button(onClick = onRetry) { Text(stringResource(Res.string.action_try_again)) }
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
    sync: SyncStatus? = null,
    syncError: String? = null,
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

        Section(stringResource(Res.string.section_name)) {
            OutlinedTextField(
                value = form.name,
                onValueChange = component::onNameChanged,
                modifier = Modifier.fillMaxWidth().onFocusChanged { isNameFocused = it.isFocused },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                placeholder = { Text(stringResource(Res.string.name_placeholder)) },
                trailingIcon = {
                    if (isNameFocused && form.name.isNotEmpty()) {
                        IconButton(onClick = { component.onNameChanged("") }) { Icon(Icons.Outlined.Cancel, contentDescription = stringResource(Res.string.name_clear)) }
                    }
                },
            )
        }

        Section(if (form.codes.size > 1) stringResource(Res.string.section_codes_found, form.codes.size) else stringResource(Res.string.section_code)) {
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
                        Text(code.format.localizedName(), style = MaterialTheme.typography.labelLarge)
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
                label = { Text(form.selectedCode.format.localizedName()) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy,
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                // The keyboard that fits what a typed code's symbology can hold.
                keyboardOptions = KeyboardOptions(
                    autoCorrectEnabled = false,
                    keyboardType = if (form.isCodeTyped && form.selectedCode.format.isNumeric) KeyboardType.Number else KeyboardType.Text,
                    capitalization = if (form.isCodeTyped && form.selectedCode.format.isUppercase) KeyboardCapitalization.Characters else KeyboardCapitalization.None,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                // Empty fields aren't errors (the add button waits for them); a code that can't be encoded is.
                isError = form.codeIssue != null,
                supportingText = form.codeIssue?.let { issue -> { Text(codeIssueText(issue, form.selectedCode.format)) } },
            )
            if (form.walletCodeFormat != form.selectedCode.format) {
                Text(
                    stringResource(Res.string.code_wallet_fallback, form.selectedCode.format.localizedName(), form.walletCodeFormat.localizedName()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Section(stringResource(Res.string.section_colour)) {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                form.colors.forEachIndexed { index, color ->
                    val selected = index == form.selectedColorIndex
                    val description = stringResource(Res.string.colour_swatch, color.spokenName(), color.displayHex)
                    // A 48 dp touch target around the 40 dp swatch; announced as a radio button with its colour.
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .selectable(selected, enabled = !busy, role = Role.RadioButton) {
                                focusManager.clearFocus()
                                component.onColorSelected(index)
                            }
                            .semantics { contentDescription = description },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(color.toColor())
                                .border(
                                    BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                                    CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected) Icon(Icons.Outlined.Check, null, tint = color.contentColor.toColor())
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (form.hasPhoto) OutlinedButton(onClick = { focusManager.clearFocus(); colorDialog = ColorDialog.PHOTO }, enabled = !busy) {
                    Icon(Icons.Outlined.Colorize, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(Res.string.colour_pick_from_photo))
                }
                OutlinedButton(onClick = { focusManager.clearFocus(); colorDialog = ColorDialog.CUSTOM }, enabled = !busy) {
                    Icon(Icons.Outlined.Palette, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(Res.string.colour_custom_button))
                }
            }
        }

        if (form.banner != null) {
            Section(stringResource(Res.string.section_banner)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .toggleable(form.isBannerEnabled, enabled = !busy, role = Role.Switch, onValueChange = component::onBannerToggled),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.banner_from_photo), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(Res.string.banner_explanation),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // The whole row toggles, so the switch is part of it rather than a second target.
                    Switch(checked = form.isBannerEnabled, onCheckedChange = null, enabled = !busy)
                }
                if (form.isBannerEnabled) {
                    val thumbnail = rememberStoredImage(form.banner?.path, maxSize = 600)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = !busy, role = Role.Button) { focusManager.clearFocus(); isEditingBanner = true }
                            .heightIn(min = 48.dp)
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(Modifier.size(width = 96.dp, height = 32.dp).clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                            if (thumbnail != null) Image(thumbnail, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        }
                        Text(stringResource(Res.string.banner_adjust), Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // A card entered by hand has no photo to cut a logo from.
        if (form.hasPhoto) CardLogoSection(form, component, enabled = !busy, onInteraction = focusManager::clearFocus)
        CardDetailsSection(form, component, enabled = !busy)

        Spacer(Modifier.height(8.dp))
    }

    // The primary action stays reachable at the bottom of the screen.
    Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Library cards sync by themselves where the wallet allows it, unless the pass is gone.
            if (form.isLibraryCard && sync != null && sync != SyncStatus.NOT_IN_WALLET) {
                SyncStatusRow(sync, syncError, onRetry = component::onRetrySyncClicked)
            } else {
                if (sync == SyncStatus.NOT_IN_WALLET) SyncStatusRow(sync, syncError, onRetry = component::onRetrySyncClicked)
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        component.onAddToWalletClicked()
                    },
                    enabled = form.isValid && !busy && hasChanges,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    if (busy) {
                        val adding = stringResource(Res.string.wallet_adding)
                        CircularProgressIndicator(
                            Modifier.size(20.dp).semantics { contentDescription = adding },
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Icon(Icons.Outlined.Wallet, null)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(if (form.isLibraryCard) Res.string.wallet_save else Res.string.wallet_add))
                    }
                }
                if (form.isLibraryCard && !hasChanges && sync == null) {
                    Text(
                        stringResource(Res.string.wallet_edit_to_save),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    }

    when (submission) {
        is Submission.Failed -> if (submission.failure != WalletFailure.CANCELLED) {
            AlertDialog(
                onDismissRequest = component::onSubmissionErrorDismissed,
                icon = { Icon(Icons.Outlined.ErrorOutline, null) },
                title = { Text(stringResource(if (form.isLibraryCard) Res.string.submission_failed_save else Res.string.submission_failed_add)) },
                text = { Text(submission.failure.message() + (submission.detail?.let { "\n\n$it" } ?: "")) },
                confirmButton = { TextButton(onClick = component::onSubmissionErrorDismissed) { Text(stringResource(Res.string.action_ok)) } },
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
        Text(
            stringResource(if (replacedExisting) Res.string.added_updated else Res.string.added_saved),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading(); liveRegion = LiveRegionMode.Polite },
        )
        Text(
            stringResource(Res.string.added_ready, form.name),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        CardPreview(form)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(26.dp)) {
            Text(stringResource(Res.string.action_done))
        }
    }
}

@Composable
private fun CardPreview(form: CardForm, compact: Boolean = false) {
    val background = form.selectedColor.toColor()
    val content = form.selectedColor.contentColor.toColor()
    val banner = rememberStoredImage(form.banner?.path?.takeIf { form.isBannerEnabled })
    val name = form.name.ifBlank { stringResource(Res.string.preview_name_placeholder) }
    val description = listOfNotNull(
        stringResource(Res.string.preview_description, name, form.walletCodeFormat.localizedName()),
        form.selectedColor.spokenName(),
        stringResource(Res.string.preview_with_logo).takeIf { form.logo.isEnabled },
        stringResource(Res.string.preview_with_banner).takeIf { banner != null },
    ).joinToString()
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            // One element for screen readers, instead of the logo, name and code pieces.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            CardLogoImage(form, size = 32.dp)
            Text(
                name,
                modifier = Modifier.padding(start = if (form.logo.isEnabled) 10.dp else 4.dp, end = 20.dp, top = if (compact) 14.dp else 20.dp, bottom = if (compact) 14.dp else 20.dp),
                color = content,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
                val code = rememberCodeImage(form.walletCodeFormat, form.selectedCode.value)
                val twoDimensional = form.walletCodeFormat.isTwoDimensional
                if (code != null) {
                    Image(
                        code,
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(if (twoDimensional) (if (compact) 96.dp else 140.dp) else (if (compact) 52.dp else 70.dp))
                            .then(if (twoDimensional) Modifier.aspectRatio(code.width.toFloat() / code.height) else Modifier.fillMaxWidth()),
                    )
                } else {
                    Text(form.walletCodeFormat.localizedName(), color = Color.Black, style = MaterialTheme.typography.labelMedium)
                }
                if (!twoDimensional || code == null) {
                    Text(
                        form.selectedCode.value,
                        color = Color.Black,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        content()
    }
}

@Composable
private fun WalletFailure.message(): String = stringResource(
    when (this) {
        WalletFailure.UNAVAILABLE -> Res.string.wallet_failure_unavailable
        WalletFailure.NOT_CONFIGURED -> Res.string.wallet_failure_not_configured
        WalletFailure.CANCELLED -> Res.string.wallet_failure_cancelled
        WalletFailure.UNSUPPORTED_CODE -> Res.string.wallet_failure_unsupported_code
        WalletFailure.FAILED -> Res.string.wallet_failure_failed
        WalletFailure.NOT_IN_WALLET -> Res.string.wallet_failure_not_in_wallet
    },
)

/** Why a typed code can't be used, in words. */
@Composable
private fun codeIssueText(issue: CodeIssue, format: CodeFormat): String {
    val name = format.localizedName()
    return when (issue) {
        CodeIssue.DIGITS_ONLY -> stringResource(Res.string.code_issue_digits, name)
        CodeIssue.LENGTH -> when (format) {
            CodeFormat.EAN_13 -> stringResource(Res.string.code_issue_length_ean13)
            CodeFormat.EAN_8 -> stringResource(Res.string.code_issue_length_ean8)
            CodeFormat.UPC_A -> stringResource(Res.string.code_issue_length_upca)
            CodeFormat.UPC_E -> stringResource(Res.string.code_issue_length_upce)
            else -> stringResource(Res.string.code_issue_length_even, name)
        }
        CodeIssue.CHECK_DIGIT -> stringResource(Res.string.code_issue_check_digit)
        CodeIssue.CHARACTERS -> when (format) {
            CodeFormat.CODE_39, CodeFormat.CODE_93 -> stringResource(Res.string.code_issue_characters_39, name)
            CodeFormat.CODABAR -> stringResource(Res.string.code_issue_characters_codabar)
            else -> stringResource(Res.string.code_issue_characters, name)
        }
    }
}

private val CodeFormat.isNumeric: Boolean
    get() = this in setOf(CodeFormat.EAN_13, CodeFormat.EAN_8, CodeFormat.UPC_A, CodeFormat.UPC_E, CodeFormat.ITF)

private val CodeFormat.isUppercase: Boolean
    get() = this == CodeFormat.CODE_39 || this == CodeFormat.CODE_93
