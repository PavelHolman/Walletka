package cz.poqet.walletka.ui.review

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Wallet
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
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
import cz.poqet.walletka.domain.WalletFailure
import cz.poqet.walletka.ui.rememberStoredImage
import cz.poqet.walletka.ui.toColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardReviewContent(component: CardReviewComponent) {
    val model by component.model.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New card") },
                navigationIcon = {
                    IconButton(onClick = component::onCloseClicked) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (val state = model) {
                is Model.Loading -> Loading(state.stage)
                is Model.Failed -> Failed(state.failure, onRetry = component::onRetryClicked, onClose = component::onCloseClicked)
                is Model.Editing -> Editing(state.form, state.submission, component)
            }
        }
    }
}

@Composable
private fun Loading(stage: LoadingStage) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            when (stage) {
                LoadingStage.IMPORTING -> "Reading the image…"
                LoadingStage.ANALYZING -> "Looking for codes…"
            },
        )
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
private fun Editing(form: CardForm, submission: Submission, component: CardReviewComponent) {
    val busy = submission == Submission.InProgress
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
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        CardPreview(form)

        Section("Name") {
            OutlinedTextField(
                value = form.name,
                onValueChange = component::onNameChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !busy,
                placeholder = { Text("e.g. Coffee Club") },
                isError = !form.isValid,
            )
            if (form.nameSuggestions.size > 1 || (form.nameSuggestions.isNotEmpty() && form.nameSuggestions.first() != form.name)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    form.nameSuggestions.forEach { name ->
                        SuggestionChip(onClick = { component.onNameChanged(name) }, label = { Text(name) }, enabled = !busy)
                    }
                }
            }
        }

        Section(if (form.codes.size > 1) "Codes found (${form.codes.size})" else "Code") {
            form.codes.forEachIndexed { index, code ->
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                            .clickable(enabled = !busy) { component.onColorSelected(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (selected) Icon(Icons.Outlined.Check, null, tint = color.contentColor.toColor())
                    }
                }
            }
        }

        if (form.banner != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Banner from photo", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Shown as the card's hero image (needs the Walletka pass service).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AdjustBannerButton(enabled = !busy, onClick = { isEditingBanner = true })
                Switch(checked = form.isBannerEnabled, onCheckedChange = component::onBannerToggled, enabled = !busy)
            }
        }

        Button(
            onClick = component::onAddToWalletClicked,
            enabled = form.isValid && !busy,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(26.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Icon(Icons.Outlined.Wallet, null)
                Spacer(Modifier.size(8.dp))
                Text("Add to Google Wallet")
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    when (submission) {
        Submission.Added -> AlertDialog(
            onDismissRequest = component::onCloseClicked,
            icon = { Icon(Icons.Outlined.CheckCircle, null) },
            title = { Text("Card added") },
            text = { Text("“${form.name}” is now in your wallet.") },
            confirmButton = { TextButton(onClick = component::onCloseClicked) { Text("Done") } },
        )

        is Submission.Failed -> if (submission.failure != WalletFailure.CANCELLED) {
            AlertDialog(
                onDismissRequest = component::onSubmissionErrorDismissed,
                icon = { Icon(Icons.Outlined.ErrorOutline, null) },
                title = { Text("Couldn't add the card") },
                text = { Text(submission.failure.message() + (submission.detail?.let { "\n\n$it" } ?: "")) },
                confirmButton = { TextButton(onClick = component::onSubmissionErrorDismissed) { Text("OK") } },
            )
        }

        else -> Unit
    }
}

@Composable
private fun CardPreview(form: CardForm) {
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
            modifier = Modifier.padding(20.dp),
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
                .padding(20.dp)
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
}
