package com.vultisig.wallet.ui.utils

import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContracts

internal class ActivityResultContractsGetContentWithMimeTypes
    (private val extraMimeTypes: Array<String>) : ActivityResultContracts.GetContent() {
    override fun createIntent(context: Context, input: String) = super.createIntent(context, input).apply {
        putExtra(Intent.EXTRA_MIME_TYPES, extraMimeTypes)
    }
}