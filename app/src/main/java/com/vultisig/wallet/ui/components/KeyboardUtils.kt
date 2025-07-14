package com.vultisig.wallet.ui.components

import android.view.ViewTreeObserver
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat


@Composable
internal fun rememberKeyboardVisibilityAsState(): State<Boolean> {
    val isImeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    return rememberUpdatedState(isImeVisible)
}


/**
 * A Composable that detects when the on-screen keyboard opens or closes.
 *
 * @param onKeyboardIsOpen Callback invoked when the keyboard becomes fully visible.
 * @param onKeyboardIsClose Callback invoked when the keyboard becomes fully hidden.
 */
@Composable
internal fun KeyboardDetector(
    onKeyboardIsOpen: () -> Unit,
    onKeyboardIsClose: () -> Unit = {},
) {
    val view = LocalView.current
    if (view.isAttachedToWindow.not()) {
        return
    }
    val viewTreeObserver = view.viewTreeObserver
    DisposableEffect(viewTreeObserver) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            val isKeyboardOpen = ViewCompat.getRootWindowInsets(view)
                ?.isVisible(WindowInsetsCompat.Type.ime())
            if (isKeyboardOpen == true)
                onKeyboardIsOpen()
            else onKeyboardIsClose()
        }

        viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose {
            viewTreeObserver.removeOnGlobalLayoutListener(listener)
        }
    }
}