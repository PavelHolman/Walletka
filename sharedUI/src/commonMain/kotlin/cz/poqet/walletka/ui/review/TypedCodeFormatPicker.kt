package cz.poqet.walletka.ui.review

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.domain.CodeFormat

/** Explains why the code is typed in and lets the user pick its symbology ([CardForm.isCodeTyped]). */
@Composable
internal fun TypedCodeFormatPicker(form: CardForm, enabled: Boolean, onSelect: (CodeFormat) -> Unit) {
    var isOpen by remember { mutableStateOf(false) }
    Column {
        Text(
            if (form.hasPhoto) "No code found in the photo. Type the number printed on the card and pick the code type."
            else "Type the number printed on the card and pick the code type.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Box {
            OutlinedButton(onClick = { isOpen = true }, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Text(form.selectedCode.format.displayName, Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowDropDown, contentDescription = "Code type")
            }
            DropdownMenu(expanded = isOpen, onDismissRequest = { isOpen = false }) {
                form.typedCodeFormats.forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format.displayName) },
                        leadingIcon = {
                            if (format == form.selectedCode.format) Icon(Icons.Outlined.Check, null) else Spacer(Modifier.size(24.dp))
                        },
                        onClick = { isOpen = false; onSelect(format) },
                    )
                }
            }
        }
    }
}
