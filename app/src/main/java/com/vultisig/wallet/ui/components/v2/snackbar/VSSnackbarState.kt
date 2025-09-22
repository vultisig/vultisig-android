package com.vultisig.wallet.ui.components.v2.snackbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds


@Stable
internal class VSSnackbarState(
    private val duration: Duration,
) {
    private var _isVisible by mutableStateOf(false)
    private var _message by mutableStateOf("")
    private var _progress by mutableFloatStateOf(0f)

    val isVisible: Boolean get() = _isVisible
    val message: String get() = _message
    val progress: Float get() = _progress

    suspend fun show(message: String) {
        _message = message
        _isVisible = true
        _progress = 0f

        val steps = 60
        val stepDuration = (duration) / steps

        repeat(steps) { step ->
            delay(stepDuration)
            _progress = (step + 1).toFloat() / steps
        }

        _isVisible = false
        _progress = 0f
    }
}

@Composable
internal fun rememberVsSnackbarState(
    duration: Duration = 2.seconds
) = remember { VSSnackbarState(duration) }

