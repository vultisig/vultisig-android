package com.vultisig.wallet.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal fun Context.closestActivityOrNull(): Activity? {
    return when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.closestActivityOrNull()
        else -> null
    }
}