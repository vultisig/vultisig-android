package com.vultisig.wallet.ui.utils

import android.annotation.SuppressLint
import android.app.Activity
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri

@SuppressLint("ComposableNaming")
@Composable
internal fun VsUriHandler(): UriHandler {
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current
    return VsUriHandler(requireNotNull(activity), uriHandler)
}

private class VsUriHandler(
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





