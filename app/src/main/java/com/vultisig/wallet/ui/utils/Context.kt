package com.vultisig.wallet.ui.utils

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal val Context.closestActivityOrNull: Activity?
    get() = when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.closestActivityOrNull
        else -> null
    }