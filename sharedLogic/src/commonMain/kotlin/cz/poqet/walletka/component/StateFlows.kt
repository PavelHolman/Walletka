package cz.poqet.walletka.component

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * A [StateFlow] derived from two others. Unlike `combine(…).stateIn(…)` its [StateFlow.value] is up to date right
 * after either source changes (it is computed on read), so callers see their own updates synchronously.
 */
internal fun <A, B, R> derivedStateOf(a: StateFlow<A>, b: StateFlow<B>, transform: (A, B) -> R): StateFlow<R> =
    DerivedStateFlow(a, b, transform)

@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
private class DerivedStateFlow<A, B, R>(
    private val a: StateFlow<A>,
    private val b: StateFlow<B>,
    private val transform: (A, B) -> R,
) : StateFlow<R> {
    override val value: R get() = transform(a.value, b.value)

    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        combine(a, b, transform).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}
