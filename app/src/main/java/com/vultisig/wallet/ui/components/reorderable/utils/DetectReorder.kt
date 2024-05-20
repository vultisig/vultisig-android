package com.vultisig.wallet.ui.components.reorderable.utils

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput


internal fun Modifier.detectReorderAfterLongPress(state: ReorderableState<*>) = then(
    Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            state.interactions.trySend(StartDrag(down.id))
        }
    }
)