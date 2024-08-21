package com.vultisig.wallet.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal val Context.closestActivityOrNull: Activity?
    get() {
        var context: Context? = this
        while (context != null) {
            when (context) {
                is Activity -> return context
                is ContextWrapper -> context = context.baseContext
            }
        }
        return null
    }