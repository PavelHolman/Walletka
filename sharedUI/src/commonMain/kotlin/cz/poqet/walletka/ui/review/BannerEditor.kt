package cz.poqet.walletka.ui.review

import androidx.compose.foundation.Image
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
    val photo = rememberStoredImage(form.photo?.path, maxSize = 2400)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust banner") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Drag and pinch the photo. The bright strip becomes the banner.",
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

/** Height of the editing area relative to the strip-shaped window; the extra space shows the photo around it. */
private const val CONTEXT_FACTOR = 2.2f

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
            .aspectRatio(BannerViewport.ASPECT_RATIO / CONTEXT_FACTOR)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black),
    ) {
        val windowPx = constraints.maxWidth.toFloat()
        val windowHeightPx = windowPx / BannerViewport.ASPECT_RATIO
        val windowTopPx = (constraints.maxHeight - windowHeightPx) / 2
        val transform = viewport.transformFor(region)
        val density = LocalDensity.current
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(viewport, windowPx) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        onRegionChange(
                            viewport.applyGesture(
                                currentRegion,
                                scaleBy = zoom,
                                anchorX = centroid.x / windowPx,
                                anchorY = (centroid.y - windowTopPx) / windowPx,
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
                        .offset {
                            IntOffset(
                                (transform.offsetX * windowPx).roundToInt(),
                                (windowTopPx + transform.offsetY * windowPx).roundToInt(),
                            )
                        }
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .requiredSize((transform.width * windowPx).toDp(), (transform.height * windowPx).toDp()),
                ) { photo() }
                // Dim the photo outside the window and outline the window.
                Canvas(Modifier.matchParentSize()) {
                    val dim = Color.Black.copy(alpha = 0.6f)
                    drawRect(dim, size = Size(size.width, windowTopPx))
                    drawRect(dim, topLeft = Offset(0f, windowTopPx + windowHeightPx), size = Size(size.width, size.height - windowTopPx - windowHeightPx))
                    drawRect(Color.White, topLeft = Offset(0f, windowTopPx), size = Size(size.width, windowHeightPx), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
