package cz.poqet.walletka.ui.review

import androidx.compose.foundation.Image
import cz.poqet.walletka.ui.toColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import cz.poqet.walletka.domain.CardColor
import cz.poqet.walletka.ui.rememberStoredImage
import kotlin.math.max
import kotlin.math.min

internal enum class ColorDialog { PHOTO, CUSTOM }

/**
 * Tap the photo to preview the colour at that point (sampled in shared code); the card only changes on "Use colour".
 */
@Composable
internal fun PhotoColorPickerDialog(
    photoPath: String,
    preview: CardColor?,
    onPick: (x: Float, y: Float) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val photo = rememberStoredImage(photoPath, maxSize = 2000)
    var marker by remember { mutableStateOf<Offset?>(null) }
    fun pick(offset: Offset, width: Float, height: Float) {
        val point = Offset((offset.x / width).coerceIn(0f, 1f), (offset.y / height).coerceIn(0f, 1f))
        marker = point
        onPick(point.x, point.y)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pick colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (photo != null) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(photo.width.toFloat() / photo.height)
                            .clip(RoundedCornerShape(8.dp))
                            .pointerInput(Unit) {
                                detectTapGestures { offset -> pick(offset, size.width.toFloat(), size.height.toFloat()) }
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, _ ->
                                    change.consume()
                                    pick(change.position, size.width.toFloat(), size.height.toFloat())
                                }
                            },
                    ) {
                        Image(photo, contentDescription = "Photo", contentScale = ContentScale.FillBounds, modifier = Modifier.matchParentSize())
                        val point = marker
                        if (point != null && preview != null) {
                            Canvas(Modifier.matchParentSize()) {
                                val center = Offset(point.x * size.width, point.y * size.height)
                                drawCircle(Color.Black.copy(alpha = 0.25f), radius = 20.dp.toPx(), center = center + Offset(0f, 2.dp.toPx()))
                                drawCircle(preview.toColor(), radius = 18.dp.toPx(), center = center)
                                drawCircle(Color.White, radius = 18.dp.toPx(), center = center, style = Stroke(3.dp.toPx()))
                            }
                        }
                    }
                } else {
                    CircularProgressIndicator()
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(width = 48.dp, height = 32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(preview?.toColor() ?: MaterialTheme.colorScheme.surfaceVariant),
                    )
                    Text(
                        preview?.hex?.uppercase() ?: "Tap or drag across the photo to pick the colour.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onConfirm, enabled = preview != null) { Text("Use colour") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Hue / saturation / brightness sliders; Material 3 has no colour picker. */
@Composable
internal fun CustomColorDialog(initial: CardColor, onPick: (CardColor) -> Unit, onDismiss: () -> Unit) {
    val start = remember(initial) { initial.toHsv() }
    var hue by remember { mutableFloatStateOf(start[0]) }
    var saturation by remember { mutableFloatStateOf(start[1]) }
    var value by remember { mutableFloatStateOf(start[2]) }
    val color = Color.hsv(hue, saturation, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Custom colour") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(color),
                )
                Text("Hue", style = MaterialTheme.typography.labelLarge)
                Slider(value = hue, onValueChange = { hue = it }, valueRange = 0f..359.9f)
                Text("Saturation", style = MaterialTheme.typography.labelLarge)
                Slider(value = saturation, onValueChange = { saturation = it })
                Text("Brightness", style = MaterialTheme.typography.labelLarge)
                Slider(value = value, onValueChange = { value = it })
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onPick(CardColor.fromArgb(color.toArgb()))
                onDismiss()
            }) { Text("Use colour") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun CardColor.toHsv(): FloatArray {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    val hi = max(r, max(g, b))
    val lo = min(r, min(g, b))
    val delta = hi - lo
    val hue = when {
        delta == 0f -> 0f
        hi == r -> 60f * (((g - b) / delta).mod(6f))
        hi == g -> 60f * ((b - r) / delta + 2f)
        else -> 60f * ((r - g) / delta + 4f)
    }
    return floatArrayOf(hue.coerceIn(0f, 359.9f), if (hi == 0f) 0f else delta / hi, hi)
}

