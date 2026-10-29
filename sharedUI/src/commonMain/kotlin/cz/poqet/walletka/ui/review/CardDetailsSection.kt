package cz.poqet.walletka.ui.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.review.CardReviewComponent
import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.domain.CardDetails
import cz.poqet.walletka.ui.resources.*
import org.jetbrains.compose.resources.stringResource
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * Optional pass details (card number, member name, expiry, notes), collapsed under "More details" unless the card
 * already has some.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CardDetailsSection(form: CardForm, component: CardReviewComponent, enabled: Boolean) {
    val details = form.details
    var isExpanded by rememberSaveable { mutableStateOf(!details.isEmpty) }
    var isPickingExpiry by rememberSaveable { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val update = { changed: CardDetails -> component.onDetailsChanged(changed) }

    if (isPickingExpiry) {
        val state = rememberDatePickerState(initialSelectedDateMillis = details.expiryEpochDay?.times(MILLIS_PER_DAY))
        DatePickerDialog(
            onDismissRequest = { isPickingExpiry = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { update(details.copy(expiryDate = CardDetails.formatDate(it.floorDiv(MILLIS_PER_DAY)))) }
                    isPickingExpiry = false
                }) { Text(stringResource(Res.string.action_ok)) }
            },
            dismissButton = { TextButton(onClick = { isPickingExpiry = false }) { Text(stringResource(Res.string.action_cancel)) } },
        ) { DatePicker(state) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val rotation by animateFloatAsState(if (isExpanded) 180f else 0f, label = "chevron")
        val expansion = stringResource(if (isExpanded) Res.string.details_expanded else Res.string.details_collapsed)
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(role = Role.Button) { focusManager.clearFocus(); isExpanded = !isExpanded }
                .semantics(mergeDescendants = true) {
                    heading()
                    stateDescription = expansion
                }
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(Res.string.details_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.details_explanation),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // The row says whether it is expanded.
            Icon(Icons.Outlined.KeyboardArrowDown, contentDescription = null, Modifier.rotate(rotation))
        }
        AnimatedVisibility(isExpanded) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val next = KeyboardOptions(imeAction = ImeAction.Next)
                OutlinedTextField(
                    value = details.memberNumber,
                    onValueChange = { update(details.copy(memberNumber = it)) },
                    label = { Text(stringResource(Res.string.details_card_number)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = next.copy(autoCorrectEnabled = false),
                    supportingText = if (details.memberNumber.isEmpty() && form.selectedCode.value.isNotBlank()) {
                        {
                            Text(
                                stringResource(Res.string.details_use_code),
                                Modifier.clickable(role = Role.Button) { update(details.copy(memberNumber = form.selectedCode.value.trim())) },
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
                OutlinedTextField(
                    value = details.memberName,
                    onValueChange = { update(details.copy(memberName = it)) },
                    label = { Text(stringResource(Res.string.details_member_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = next.copy(capitalization = KeyboardCapitalization.Words),
                )
                OutlinedTextField(
                    value = details.expiryEpochDay?.let(CardDetails::formatDate).orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.details_expiry)) },
                    placeholder = { Text(stringResource(Res.string.details_expiry_none)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = enabled,
                    trailingIcon = {
                        Row {
                            if (details.expiryDate.isNotEmpty()) {
                                IconButton(onClick = { update(details.copy(expiryDate = "")) }, enabled = enabled) {
                                    Icon(Icons.Outlined.Cancel, stringResource(Res.string.details_expiry_remove))
                                }
                            }
                            IconButton(onClick = { focusManager.clearFocus(); isPickingExpiry = true }, enabled = enabled) {
                                Icon(Icons.Outlined.DateRange, stringResource(Res.string.details_expiry_pick))
                            }
                        }
                    },
                )
                OutlinedTextField(
                    value = details.notes,
                    onValueChange = { update(details.copy(notes = it)) },
                    label = { Text(stringResource(Res.string.details_notes)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 6,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Default),
                )
            }
        }
    }
}
