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
    // A card entered by hand has no photo to cut a banner from.
    val photo = form.photo ?: return
    CropEditorDialog(
        title = "Adjust banner",
        hint = "Drag and pinch the photo. The bright strip becomes the banner.",
        photoPath = photo.path,
        viewport = remember(form.photoAspectRatio) { form.bannerViewport },
        initialRegion = form.bannerRegion,
        onDone = onDone,
        onDismiss = onDismiss,
    )
}

/** Pan & pinch [photoPath] under a fixed window with [viewport]'s aspect ratio to choose a crop of it. */
@Composable
internal fun CropEditorDialog(
    title: String,
    hint: String,
    photoPath: String,
    viewport: BannerViewport,
    initialRegion: NormalizedRect,
    onDone: (NormalizedRect) -> Unit,
    onDismiss: () -> Unit,
) {
    var region by remember(photoPath) { mutableStateOf(initialRegion) }
    val photo = rememberStoredImage(photoPath, maxSize = 2400)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    hint,
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

/** Height of the editing area relative to a strip-shaped window; the extra space shows the photo around it. */
private const val CONTEXT_FACTOR = 2.2f

/** Narrower windows (e.g. a square logo) leave this much of the area's width around them. */
private const val MAX_WINDOW_HEIGHT_FRACTION = 0.6f

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
        // The window spans the full width, unless that would make it too tall for the area.
        val windowPx = minOf(constraints.maxWidth.toFloat(), constraints.maxHeight * MAX_WINDOW_HEIGHT_FRACTION * viewport.aspectRatio)
        val windowHeightPx = windowPx / viewport.aspectRatio
        val windowLeftPx = (constraints.maxWidth - windowPx) / 2
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
                                anchorX = (centroid.x - windowLeftPx) / windowPx,
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
                                (windowLeftPx + transform.offsetX * windowPx).roundToInt(),
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
                    drawRect(dim, topLeft = Offset(0f, windowTopPx), size = Size(windowLeftPx, windowHeightPx))
                    drawRect(dim, topLeft = Offset(windowLeftPx + windowPx, windowTopPx), size = Size(size.width - windowLeftPx - windowPx, windowHeightPx))
                    drawRect(Color.White, topLeft = Offset(windowLeftPx, windowTopPx), size = Size(windowPx, windowHeightPx), style = Stroke(2.dp.toPx()))
                }
            }
        }
    }
}
