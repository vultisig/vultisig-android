package com.vultisig.wallet.app.activity.components

import android.app.Activity
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.vultisig.wallet.ui.widgets.market.EXTRA_LAUNCHED_FROM_MARKET_WIDGET

@Composable
internal fun Activity.CheckDeeplink(onDeeplinkFound: (Uri) -> Unit) {
    LaunchedEffect(Unit) {
        // A home-screen widget tap arrives with a Glance-generated data URI that is not a link
        // or a file; routing it would land the user on the Import screen with an error.
        if (intent.getBooleanExtra(EXTRA_LAUNCHED_FROM_MARKET_WIDGET, false)) return@LaunchedEffect
        val uri = intent.data
        if (uri != null) {
            onDeeplinkFound(uri)
        }
    }
}
