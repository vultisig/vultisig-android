package com.vultisig.wallet.presenter.common

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.composed
import kotlinx.coroutines.delay
import  androidx.compose.ui.Modifier


fun Modifier.clickOnce(enabled: Boolean = true, onClick: () -> Unit) =
    this.composed {
        var enableAgain by remember { mutableStateOf(true) }
        LaunchedEffect(enableAgain, block = {
            if (enableAgain) return@LaunchedEffect
            /**/  delay(timeMillis = 900L)
            enableAgain = true
        })
        Modifier.clickable(enabled = enabled) {
            if (enableAgain) {
                enableAgain = false
                onClick()
            }
        }
    }

@Composable
fun ClickOnce(
    onClick: () -> Unit
): () -> Unit {
    var lastClickTime by remember { mutableLongStateOf(0L) }

    return {
        println("Before click: lastClickTime = $lastClickTime")
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= 500L) {
            lastClickTime = currentTime
            println("After click: lastClickTime = $lastClickTime")
            onClick()
        }
    }
}
