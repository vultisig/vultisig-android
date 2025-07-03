package com.vultisig.wallet.ui.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import timber.log.Timber

internal object VsClipboardService {

    fun copy(context: Context, value: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(value, value)
        clipboard.setPrimaryClip(clip)
    }

    fun getClipboardData(context: Context): String {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        try {
            val primaryClip = clipboard.primaryClip
            return primaryClip?.getItemAt(primaryClip.itemCount - 1)?.text.toString()
        } catch (e: Exception) {
            Timber.e(e)
            return ""
        }
    }

}
