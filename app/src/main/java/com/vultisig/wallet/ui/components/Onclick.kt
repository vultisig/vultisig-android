package com.vultisig.wallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlinx.coroutines.delay

private const val COOL_DOWN_PERIOD = 375L
private var lastClickTime = 0L

fun Modifier.clickOnce(coolDownPeriod: Long = COOL_DOWN_PERIOD,enabled: Boolean = true, onClick: () -> Unit): Modifier = this.composed {
    var enableAgain by remember { mutableStateOf(true) }

    LaunchedEffect(enableAgain) {
        if (enableAgain)
            return@LaunchedEffect
        delay(timeMillis = coolDownPeriod)
        enableAgain = true
    }

    clickable(enabled = enabled) {
        if (enableAgain) {
            enableAgain = false
            onClick()
        }
    }
}


fun clickOnce(onClick: () -> Unit): () -> Unit {
    return {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= COOL_DOWN_PERIOD) {
            lastClickTime = currentTime
            onClick()
        }
    }
}

fun clickOnce(coolDownPeriod: Long = COOL_DOWN_PERIOD, onClick: () -> Unit): () -> Unit {
    return {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastClickTime >= coolDownPeriod) {
            lastClickTime = currentTime
            onClick()
        }
    }
}
