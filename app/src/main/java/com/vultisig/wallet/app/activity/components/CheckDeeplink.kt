package com.vultisig.wallet.app.activity.components

import android.app.Activity
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
internal fun Activity.CheckDeeplink(
    onDeeplinkFound: (Uri) -> Unit,
) {
    LaunchedEffect(Unit) {
        val uri = intent.data
        if (uri != null) {
            onDeeplinkFound(uri)
        }
    }
}