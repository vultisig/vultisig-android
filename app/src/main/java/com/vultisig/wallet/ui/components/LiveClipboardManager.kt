package com.vultisig.wallet.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import com.vultisig.wallet.ui.utils.VsClipboardService

@Composable
internal fun rememberClipboardText(
    filter: (String?) -> Boolean = { true },
): State<String?> {

    val currentText = VsClipboardService.getClipboardData()

    val text = remember {
        derivedStateOf {
            val value = currentText.value
            value.takeIf { filter(value) }
        }
    }

    onClipDataChanged {
        val clipText = this?.getItemAt(0)?.text?.toString()
        currentText.value = clipText.takeIf { filter(clipText) }
    }

    return text
}

@SuppressLint("ComposableNaming")
@Composable
private fun onClipDataChanged(onPrimaryClipChanged: ClipData?.() -> Unit) {
    val context = LocalContext.current
    val windowInfo = LocalWindowInfo.current
    val isWindowFocused = windowInfo.isWindowFocused

    LaunchedEffect(context, isWindowFocused) {
        val clipboardManager =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        onPrimaryClipChanged(clipboardManager.primaryClip)
    }
}