package com.vultisig.wallet.ui.utils

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

internal val LocalVsUriHandler: ProvidableCompositionLocal<UriHandler>
    @Composable
    get() {
        val activity = LocalContext.current.closestActivityOrNull()
        val uriHandler = LocalUriHandler.current
        return staticCompositionLocalOf {
            VsUriHandler(requireNotNull(activity), uriHandler)
        }
    }

internal class VsUriHandler(
    private val activity: Activity,
    private val uriHandler: UriHandler,
) : UriHandler {

    override fun openUri(
        uri: String,
    ) = if (uri.isCctLink)
        activity.openCct(uri.toUri())
    else
        uriHandler.openUri(uri)


    private val String.isCctLink: Boolean
        get() = listOf(
            VsAuxiliaryLinks.PRIVACY,
            VsAuxiliaryLinks.TERMS_OF_SERVICE,
            VsAuxiliaryLinks.VULT,
        ).contains(this)

}





