package com.vultisig.wallet.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal object VsClipboardService {

    fun copy(context: Context, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return
        val clip = ClipData.newPlainText(
            value,
            value
        )
        clipboard.setPrimaryClip(clip)
    }

    @Composable
    fun getClipboardData(): MutableState<String?> {
        var text = remember {
            mutableStateOf<String?>(null)
        }

        val clipboardManager =
            LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

        LaunchedEffect(Unit) {
            val clipData: ClipData? = clipboardManager.primaryClip
            clipData?.let {
                text.value = clipData.getItemAt(0).text?.toString()
            }
        }

        return text
    }
}
