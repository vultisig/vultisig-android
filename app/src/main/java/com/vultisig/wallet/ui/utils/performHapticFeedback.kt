package com.vultisig.wallet.ui.utils

import android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING
import android.view.HapticFeedbackConstants.VIRTUAL_KEY
import android.view.View

fun View.performHaptic(){
    performHapticFeedback(
        VIRTUAL_KEY,
        FLAG_IGNORE_GLOBAL_SETTING
    )
}