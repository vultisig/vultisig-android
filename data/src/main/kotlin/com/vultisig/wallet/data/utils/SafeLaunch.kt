package com.vultisig.wallet.data.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Launches a coroutine that catches all exceptions except [CancellationException].
 *
 * Use this instead of [CoroutineScope.launch] for coroutines that perform
 * network calls, deserialization, or other operations that may throw
 * unhandled exceptions. This prevents crashes while still logging the error.
 *
 * [CancellationException] is always re-thrown to preserve coroutine cancellation
 * semantics (e.g. `viewModelScope` cancellation on `ViewModel.onCleared()`).
 *
 * [onError] is intentionally **non-suspend** — it runs synchronously on the
 * coroutine's dispatcher, which is safe for updating `MutableStateFlow` values.
 * If [onError] itself throws, that exception is **not** caught and will propagate normally.
 *
 * Usage in a ViewModel:
 * ```
 * viewModelScope.safeLaunch(
 *     onError = { e ->
 *         _uiState.update { it.copy(error = e.message) }
 *     }
 * ) {
 *     val data = repository.fetchData()
 *     _uiState.update { it.copy(data = data) }
 * }
 * ```
 *
 * @param onError handler for caught exceptions. Defaults to logging via Timber.
 * @param block the coroutine body to execute.
 * @return the [Job] for the launched coroutine.
 */
fun CoroutineScope.safeLaunch(
    onError: (Throwable) -> Unit = { Timber.e(it, "Unhandled exception in coroutine") },
    block: suspend CoroutineScope.() -> Unit,
): Job = launch {
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        onError(e)
    }
}
