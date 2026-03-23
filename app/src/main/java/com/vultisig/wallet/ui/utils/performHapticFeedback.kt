package com.vultisig.wallet.ui.utils

import android.os.Build
import android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
import android.view.HapticFeedbackConstants.VIRTUAL_KEY
import android.view.View

fun View.performHaptic() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        performHapticFeedback(VIRTUAL_KEY)
    } else {
        @Suppress("DEPRECATION") performHapticFeedback(VIRTUAL_KEY, FLAG_IGNORE_GLOBAL_SETTING)
    }
}
