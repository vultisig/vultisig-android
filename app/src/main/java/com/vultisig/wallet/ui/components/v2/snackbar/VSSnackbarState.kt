package com.vultisig.wallet.ui.components.v2.snackbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
@Stable
internal class VSSnackbarState(
    private val duration: Duration,
    private val coroutineScope: CoroutineScope,
) {
    private val _progressState = MutableStateFlow(ProgressState())
    val progressState: StateFlow<ProgressState> = _progressState
    private val _showProgress = MutableSharedFlow<Pair<String, SnackbarType>>()

    init {
        _showProgress
            .flatMapLatest { (message, type) ->
                flow {
                    val steps = 60
                    val stepDuration = duration / steps
                    repeat(steps) { step ->
                        delay(stepDuration)
                        emit(
                            ProgressState(
                                message = message,
                                isVisible = true,
                                progress = (step + 1).toFloat() / steps,
                                type = type,
                            )
                        )
                    }
                    emit(ProgressState(message = "", isVisible = false, progress = 0f))
                }
            }
            .onEach { state -> _progressState.update { state } }
            .launchIn(coroutineScope)
    }

    fun show(message: String, type: SnackbarType = SnackbarType.Success) {
        coroutineScope.launch { _showProgress.emit(message to type) }
    }
}

internal enum class SnackbarType {
    Success,
    Warning,
    Error,
}

internal data class ProgressState(
    val message: String = "",
    val isVisible: Boolean = false,
    val progress: Float = 0f,
    val type: SnackbarType = SnackbarType.Success,
)

@Composable
internal fun rememberVsSnackbarState(duration: Duration = 1.seconds): VSSnackbarState {
    val coroutineScope = rememberCoroutineScope()
    return remember { VSSnackbarState(duration, coroutineScope) }
}
