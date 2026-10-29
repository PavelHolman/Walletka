package cz.poqet.walletka.ui.home

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * Drag-to-reorder for a `LazyColumn` whose items are keyed by id: the dragged item follows the finger and swaps
 * places with the items it passes; [onMove] reports the whole move (from, to) once it is dropped.
 */
@Stable
class ReorderState internal constructor(private val listState: LazyListState) {
    internal var onMove: (from: Int, to: Int) -> Unit = { _, _ -> }

    var draggedKey by mutableStateOf<String?>(null)
        private set

    internal var draggedOffset by mutableFloatStateOf(0f)
        private set

    /** The order shown while dragging; ids of the reorderable items only. */
    var order by mutableStateOf<List<String>>(emptyList())
        private set

    private var startIndex = -1

    /** Ids of the reorderable items in their current order; set on every composition. */
    internal var keys: List<String> = emptyList()

    internal fun start(key: String) {
        order = keys
        startIndex = keys.indexOf(key)
        draggedOffset = 0f
        draggedKey = key
    }

    internal fun drag(delta: Float) {
        draggedOffset += delta
        val key = draggedKey ?: return
        val items = listState.layoutInfo.visibleItemsInfo.filter { it.key in order }
        val dragged = items.firstOrNull { it.key == key } ?: return
        val from = order.indexOf(key)
        // Wait for the list to lay out the previous swap.
        val first = items.first()
        if (dragged.index - first.index + order.indexOf(first.key as String) != from) return
        val center = dragged.offset + draggedOffset + dragged.size / 2
        val target = items.firstOrNull { it.key != key && center >= it.offset && center < it.offset + it.size } ?: return
        val to = order.indexOf(target.key as String)
        order = order.toMutableList().apply { add(to, removeAt(from)) }
        // The item is laid out in its new place next; keep it under the finger.
        val shift = if (to > from) (target.offset + target.size) - (dragged.offset + dragged.size) else target.offset - dragged.offset
        draggedOffset -= shift
    }

    internal fun end() {
        val to = order.indexOf(draggedKey)
        if (startIndex >= 0 && to >= 0 && to != startIndex) onMove(startIndex, to)
        draggedKey = null
        draggedOffset = 0f
        startIndex = -1
    }
}

@Composable
fun rememberReorderState(listState: LazyListState, keys: List<String>, onMove: (from: Int, to: Int) -> Unit): ReorderState {
    val state = remember(listState) { ReorderState(listState) }
    state.onMove = rememberUpdatedState(onMove).value
    state.keys = keys
    return state
}

/** Lifts the item while it is dragged, and animates the others into their new places. */
fun Modifier.reorderableItem(scope: LazyItemScope, state: ReorderState, key: String): Modifier =
    if (state.draggedKey == key) {
        zIndex(1f).graphicsLayer { translationY = state.draggedOffset }.shadow(8.dp, RoundedCornerShape(16.dp))
    } else {
        with(scope) { animateItem() }
    }

/** Starts dragging the item with [key] from this handle. */
fun Modifier.reorderHandle(state: ReorderState, key: String): Modifier =
    pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = state::end,
            onDragCancel = state::end,
        )
    }
