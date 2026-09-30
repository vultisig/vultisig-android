package com.vultisig.wallet.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

internal object VsClipboardService {

    /**
     * Whether the app should confirm a copy with its own message.
     *
     * Android 13+ confirms every copy with a system clipboard preview, so a second message from the
     * app only duplicates it; below 13 the system shows nothing. Copy controls also confirm in
     * place (see [com.vultisig.wallet.ui.components.CopyIcon]), which covers OEM builds that drop
     * the system preview.
     */
    fun needsCopyConfirmation(sdkInt: Int = Build.VERSION.SDK_INT): Boolean =
        sdkInt < Build.VERSION_CODES.TIRAMISU

    fun copy(context: Context, value: String) {
        val clipboard =
            context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(value, value)
        clipboard.setPrimaryClip(clip)
    }

    @Composable
    fun getClipboardData(): MutableState<String?> {
        val text = remember { mutableStateOf<String?>(null) }

        val clipboardManager =
            LocalContext.current.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                ?: return text

        LaunchedEffect(Unit) {
            try {
                val clipData: ClipData? = clipboardManager.primaryClip
                clipData?.let { text.value = clipData.getItemAt(0).text?.toString() }
            } catch (e: Exception) {
                text.value = null
            }
        }

        return text
    }
}
