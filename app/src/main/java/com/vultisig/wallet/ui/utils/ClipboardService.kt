package com.vultisig.wallet.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import timber.log.Timber

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

    fun getClipboardData(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            val primaryClip = clipboard.primaryClip
            if (primaryClip != null && primaryClip.itemCount > 0) {
                return primaryClip.getItemAt(0)?.text?.toString() ?: ""
            }
            return ""
        } catch (e: Exception) {
            Timber.e(e)
            return ""
        }
    }

}
