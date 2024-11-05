package com.vultisig.wallet.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import kotlinx.coroutines.delay

private const val COOL_DOWN_PERIOD = 2000L
private var lastClickTime = 0L
private var lastHashCode = 0

fun Modifier.clickOnce(onClick: () -> Unit): Modifier = this.composed {
    Modifier.clickable() {
        val currentTime = System.currentTimeMillis()
        if (lastHashCode != onClick.hashCode() || currentTime - lastClickTime > COOL_DOWN_PERIOD) {
            lastHashCode = onClick.hashCode()
            lastClickTime = currentTime
            onClick()
        }
    }
}

@Composable
fun clickOnce(onClick: () -> Unit): () -> Unit {
    return {
        val currentTime = System.currentTimeMillis()
        if (lastHashCode != onClick.hashCode() || currentTime - lastClickTime > COOL_DOWN_PERIOD) {
            lastHashCode = onClick.hashCode()
            lastClickTime = currentTime
            onClick()
        }
    }
}
