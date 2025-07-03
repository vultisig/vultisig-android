package com.vultisig.wallet.ui.components

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.vultisig.wallet.ui.utils.VsClipboardService

@Composable
internal fun rememberClipboardText(
    filter: (String?) -> Boolean = { true },
): State<String?> {
    val context = LocalContext.current
    val text = remember {
        val clipText = VsClipboardService.getClipboardData(context)
        mutableStateOf(
            if (filter(clipText)) {
                clipText
            } else null
        )
    }

    onClipDataChanged {
        val clipText = VsClipboardService.getClipboardData(context)
        if (filter(clipText)) {
            text.value = VsClipboardService.getClipboardData(context)
        }
    }

    return text
}

@SuppressLint("ComposableNaming")
@Composable
private fun onClipDataChanged(onPrimaryClipChanged: ClipData?.() -> Unit) {
    val clipboardManager =
        LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val callback = remember {
        ClipboardManager.OnPrimaryClipChangedListener {
            onPrimaryClipChanged(clipboardManager.primaryClip)
        }
    }
    DisposableEffect(clipboardManager) {
        clipboardManager.addPrimaryClipChangedListener(callback)
        onDispose {
            clipboardManager.removePrimaryClipChangedListener(callback)
        }
    }
}