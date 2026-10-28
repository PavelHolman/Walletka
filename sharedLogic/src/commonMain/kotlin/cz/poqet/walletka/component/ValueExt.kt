package cz.poqet.walletka.component

import com.arkivanov.decompose.value.Value
import com.arkivanov.essenty.lifecycle.Lifecycle
import com.arkivanov.essenty.lifecycle.doOnDestroy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mirrors a Decompose [Value] into a [StateFlow] for as long as [lifecycle] lives.
 *
 * Swift Export bridges `StateFlow<T>` with its element type intact (`KotlinTypedStateFlow<T>`), while `Value<T>`
 * is exported type-erased, so Swift-facing state is exposed as flows.
 */
internal fun <T : Any, R> Value<T>.toStateFlow(lifecycle: Lifecycle, transform: (T) -> R): StateFlow<R> {
    val flow = MutableStateFlow(transform(value))
    val cancellation = subscribe { flow.value = transform(it) }
    lifecycle.doOnDestroy(cancellation::cancel)
    return flow.asStateFlow()
}
