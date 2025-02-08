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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString

@Composable
internal fun rememberClipboardText(
    filter: (AnnotatedString?) -> Boolean = { true },
): State<AnnotatedString?> {
    val clipboardManager = LocalClipboardManager.current

    val text = remember {
        val clipText = clipboardManager.getText()
        mutableStateOf(
            if (filter(clipText)) {
                clipText
            } else null
        )
    }

    onClipDataChanged {
        val clipText = clipboardManager.getText()
        if (filter(clipText)) {
            text.value = clipboardManager.getText()
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