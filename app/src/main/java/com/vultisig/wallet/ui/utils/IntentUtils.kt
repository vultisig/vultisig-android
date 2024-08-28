package com.vultisig.wallet.ui.utils

import android.content.Intent

internal fun createBackupFileIntent(backupFileName: String) =
    Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "application/octet-stream"
        putExtra(Intent.EXTRA_TITLE, backupFileName)
    }
