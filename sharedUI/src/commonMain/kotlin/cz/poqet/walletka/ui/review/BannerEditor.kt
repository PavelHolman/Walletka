package cz.poqet.walletka.ui.review

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.component.review.CardReviewComponent.CardForm
import cz.poqet.walletka.domain.BannerViewport
import cz.poqet.walletka.domain.NormalizedRect
import cz.poqet.walletka.ui.rememberStoredImage
import kotlin.math.roundToInt

/**
 * Pan & pinch the photo under a fixed strip-shaped window to choose the banner. The geometry is the shared
 * [BannerViewport], so it behaves like the iOS editor.
 */
@Composable
internal fun BannerEditorDialog(form: CardForm, onDone: (NormalizedRect) -> Unit, onDismiss: () -> Unit) {
    val viewport = remember(form.photoAspectRatio) { form.bannerViewport }
    var region by remember(form.photo) { mutableStateOf(form.bannerRegion) }
    val photo = rememberStoredImage(form.photo.path, maxSize = 2400)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust banner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Drag and pinch the photo to choose what the banner shows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                BannerWindow(viewport, region, onRegionChange = { region = it }) {
                    if (photo != null) {
                        Image(photo, contentDescription = "Photo", contentScale = ContentScale.FillBounds)
                    } else {
                        CircularProgressIndicator(Modifier.wrapContentSize())
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(region) }) { Text("Done") } },
        dismissButton = {
            Row {
                TextButton(onClick = { region = viewport.clamp(NormalizedRect(0f, 0f, 1f, 1f)) }) { Text("Fit") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

@Composable
private fun BannerWindow(
    viewport: BannerViewport,
    region: NormalizedRect,
    onRegionChange: (NormalizedRect) -> Unit,
    photo: @Composable () -> Unit,
) {
    val currentRegion by rememberUpdatedState(region)
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .aspectRatio(BannerViewport.ASPECT_RATIO)
            .clipToBounds()
            .background(Color.Black)
            .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)),
    ) {
        val windowPx = constraints.maxWidth.toFloat()
        val transform = viewport.transformFor(region)
        val density = LocalDensity.current
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(BannerViewport.ASPECT_RATIO)
                .pointerInput(viewport, windowPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        onRegionChange(
                            viewport.applyGesture(
                                currentRegion,
                                scaleBy = zoom,
                                anchorX = centroid.x / windowPx,
                                anchorY = centroid.y / windowPx,
                                panX = pan.x / windowPx,
                                panY = pan.y / windowPx,
                            ),
                        )
                    }
                },
        ) {
            with(density) {
                Box(
                    Modifier
                        .offset { IntOffset((transform.offsetX * windowPx).roundToInt(), (transform.offsetY * windowPx).roundToInt()) }
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize((transform.width * windowPx).toDp(), (transform.height * windowPx).toDp()),
                ) { photo() }
            }
        }
    }
}

/** Small "Adjust" affordance next to the banner switch. */
@Composable
internal fun AdjustBannerButton(enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.padding(end = 4.dp)) { Text("Adjust") }
}
