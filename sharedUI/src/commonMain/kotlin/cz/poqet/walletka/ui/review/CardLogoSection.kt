package cz.poqet.walletka.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.review.CardReviewComponent
import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.ui.rememberStoredImage

/** Turns the logo cut out of the photo on and off and opens its crop editor. */
@Composable
internal fun CardLogoSection(form: CardForm, component: CardReviewComponent, enabled: Boolean, onInteraction: () -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    val photo = form.photo
    if (isEditing && photo != null) {
        CropEditorDialog(
            title = "Adjust logo",
            hint = "Drag and pinch the photo. The bright square becomes the logo, shown in a circle.",
            photoPath = photo.path,
            viewport = remember(form.photoAspectRatio, form.walletPlatform) { form.logoViewport },
            initialRegion = form.logo.region,
            onDone = { region ->
                component.onLogoRegionChanged(region)
                if (!form.logo.isEnabled) component.onLogoToggled(true)
                isEditing = false
            },
            onDismiss = { isEditing = false },
        )
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Logo", style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Logo from photo", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "The brand logo cut out of your photo, shown in a circle on the card.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = form.logo.isEnabled, onCheckedChange = { onInteraction(); component.onLogoToggled(it) }, enabled = enabled)
        }
        if (form.logo.isEnabled) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onInteraction(); isEditing = true }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CardLogoImage(form, size = 40.dp)
                Text("Adjust logo", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The logo as Google Wallet shows it, in a circle; nothing while it is off. */
@Composable
internal fun CardLogoImage(form: CardForm, size: Dp, modifier: Modifier = Modifier) {
    if (!form.logo.isEnabled) return
    val logo = rememberStoredImage(form.logo.image?.path, maxSize = 300)
    Box(modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)) {
        if (logo != null) Image(logo, contentDescription = "Logo", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    }
}
