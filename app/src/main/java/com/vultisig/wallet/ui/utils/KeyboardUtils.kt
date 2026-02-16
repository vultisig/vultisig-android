package com.vultisig.wallet.ui.utils

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity


@Composable
private fun rememberImeState(): State<Boolean> {
    val density = LocalDensity.current
    val imeInsets = WindowInsets.ime

    return remember {
        derivedStateOf {
            imeInsets.getBottom(density) > 0
        }
    }
}

@Composable
fun KeyboardAware(
    onKeyboardChanged: (Boolean) -> Unit
) {
    val isKeyboardOpen by rememberImeState()

    LaunchedEffect(isKeyboardOpen) {
        onKeyboardChanged(isKeyboardOpen)
    }
}