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
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.outlined.Colorize
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    val isLibraryCard = (model as? Model.Editing)?.form?.isLibraryCard == true
                    Text(stringResource(if (isLibraryCard) Res.string.review_title_edit else Res.string.review_title_new))
                },
                navigationIcon = {
                    IconButton(onClick = component::onCloseClicked) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(Res.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        // The Scaffold's insets are used here, so the keyboard padding below doesn't add them again.
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (val state = model) {
                is Model.Loading -> Loading(state.stage, state.photo?.path)
                is Model.Failed -> Failed(state.failure, onRetry = component::onRetryClicked, onClose = component::onCloseClicked)
                is Model.Editing -> Column {
                    state.duplicateOf?.let { DuplicateCardBanner(it, onOpen = component::onOpenDuplicateClicked) }
                    Editing(state.form, state.submission, state.colorPreview, component, state.sync, state.syncError)
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
                AnalysisFailure.CARD_NOT_FOUND -> stringResource(Res.string.failure_card_not_found)
            },
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
            OutlinedButton(onClick = onClose) { Text(stringResource(Res.string.action_back)) }
            if (failure == AnalysisFailure.DETECTION_FAILED) Button(onClick = onRetry) { Text(stringResource(Res.string.action_try_again)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Editing(
    form: CardForm,
    submission: Submission,
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
    // imePadding: the fields lower in the form (details, notes) scroll above the keyboard.
    Column(Modifier.fillMaxSize().imePadding()) {
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
        CardPreview(form, CardPreviewSize.COMPACT)

        // Typing a card in: the keyboard's action key moves on from the name to the missing code.
        val needsTypedCode = form.isCodeTyped && form.selectedCode.value.isEmpty()
        val codeFocus = remember { FocusRequester() }
        Section(stringResource(Res.string.section_name)) {
            OutlinedTextField(
                value = form.name,
                onValueChange = component::onNameChanged,
                modifier = Modifier.fillMaxWidth().onFocusChanged { isNameFocused = it.isFocused },
                singleLine = true,
                enabled = !busy,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = if (needsTypedCode) ImeAction.Next else ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }, onNext = { codeFocus.requestFocus() }),
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
                modifier = Modifier.fillMaxWidth().focusRequester(codeFocus),
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

    // Library cards save by themselves; the bar shows how the wallet is keeping up, where it is updated silently.
    val showsSync = sync != null && sync != SyncStatus.NOT_IN_WALLET
    if (!form.isLibraryCard || showsSync) Surface(tonalElevation = 3.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (form.isLibraryCard) {
                if (sync != null) SyncStatusRow(sync, syncError, onRetry = component::onRetrySyncClicked)
            } else {
                // Saved to Walletka; the card's preview then offers the wallet.
                Button(
                    onClick = {
                        focusManager.clearFocus()
                        component.onSaveClicked()
                    },
                    enabled = form.isValid && !busy,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                    shape = RoundedCornerShape(26.dp),
                ) {
                    if (busy) {
                        ButtonProgress(stringResource(Res.string.review_saving), MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Text(stringResource(Res.string.review_save))
                    }
                }
                // Why the button is disabled; a code that can't be encoded is explained at the code.
                if (!busy && form.name.isBlank()) {
                    Text(
                        stringResource(Res.string.wallet_needs_name),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                } else if (!busy && form.selectedCode.value.isBlank()) {
                    Text(
                        stringResource(Res.string.wallet_needs_code),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
    }
}

/** A spinner in place of a button's label, announced as [description]. */
@Composable
internal fun ButtonProgress(description: String, color: Color) {
    CircularProgressIndicator(
        Modifier.size(20.dp).semantics { contentDescription = description },
        strokeWidth = 2.dp,
        color = color,
    )
}

/** How big [CardPreview] draws the card: in the form, on its own, or to be scanned at a till. */
internal enum class CardPreviewSize { COMPACT, REGULAR, LARGE }

@Composable
internal fun CardPreview(
    form: CardForm,
    size: CardPreviewSize = CardPreviewSize.REGULAR,
    modifier: Modifier = Modifier,
    showName: Boolean = true,
) {
    val compact = size == CardPreviewSize.COMPACT
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
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(background)
            // One element for screen readers, instead of the logo, name and code pieces.
            .clearAndSetSemantics { contentDescription = description },
    ) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            CardLogoImage(form, size = 32.dp)
            if (showName) {
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
                val codeHeight = when (size) {
                    CardPreviewSize.COMPACT -> if (twoDimensional) 96.dp else 52.dp
                    CardPreviewSize.REGULAR -> if (twoDimensional) 140.dp else 70.dp
                    CardPreviewSize.LARGE -> if (twoDimensional) 240.dp else 120.dp
                }
                if (code != null) {
                    Image(
                        code,
                        contentDescription = null,
                        filterQuality = FilterQuality.None,
                        // A linear code is one module row: stretch it to the bars' height.
                        contentScale = if (twoDimensional) ContentScale.Fit else ContentScale.FillBounds,
                        // A 2D code as big as fits up to its height (a wide PDF417 is narrowed to the card).
                        modifier = if (twoDimensional && size == CardPreviewSize.LARGE) {
                            // To be scanned, a 2D code takes the card's whole width: dense codes need every dp.
                            Modifier.fillMaxWidth().aspectRatio(code.width.toFloat() / code.height)
                        } else if (twoDimensional) {
                            Modifier.heightIn(max = codeHeight).aspectRatio(code.width.toFloat() / code.height)
                        } else {
                            Modifier.height(codeHeight).fillMaxWidth()
                        },
                    )
                } else {
                    // Where the code goes, as big as the code will be, so the card doesn't grow while it's typed in.
                    Box(
                        if (twoDimensional) Modifier.heightIn(max = codeHeight).aspectRatio(1f) else Modifier.height(codeHeight).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(form.walletCodeFormat.localizedName(), color = Color.Gray, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (!twoDimensional) {
                    Text(
                        form.selectedCode.value,
                        color = Color.Black,
                        style = (if (size == CardPreviewSize.LARGE) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall)
                            .copy(fontFamily = FontFamily.Monospace),
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
internal fun WalletFailure.message(): String = stringResource(
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
