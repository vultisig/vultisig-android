package com.vultisig.wallet.ui.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.core.net.toUri
import com.vultisig.wallet.ui.models.OnRampViewModel.Companion.BANXA_URL
import timber.log.Timber

@SuppressLint("ComposableNaming")
@Composable
internal fun VsUriHandler(): UriHandler {
    val isPreview = LocalInspectionMode.current
    val activity = LocalActivity.current
    val uriHandler = LocalUriHandler.current

    return if (isPreview || activity == null) {
        uriHandler
    } else {
        VsUriHandler(activity, uriHandler)
    }
}

private class VsUriHandler(private val activity: Activity, private val uriHandler: UriHandler) :
    UriHandler {

    override fun openUri(uri: String) {
        try {
            if (uri.isCctLink) activity.openCct(uri.toUri()) else uriHandler.openUri(uri)
        } catch (e: IllegalArgumentException) {
            Timber.e(e, "Failed to open URI: $uri")
        } catch (e: ActivityNotFoundException) {
            fallbackToBrowser(uri, e)
        } catch (e: SecurityException) {
            fallbackToBrowser(uri, e)
        }
    }

    /**
     * Logs [error] and retries opening [uri] via a chooser-based browser launch.
     *
     * Used when the direct launch is rejected by the platform (e.g. `SecurityException` from
     * `checkStartAnyActivityPermission`) so the failure degrades gracefully instead of crashing.
     */
    private fun fallbackToBrowser(uri: String, error: Exception) {
        Timber.e(error, "Failed to open URI, falling back to browser: $uri")
        activity.openInBrowser(uri.toUri())
    }

    private val String.isCctLink: Boolean
        get() =
            listOf(
                    VsAuxiliaryLinks.PRIVACY,
                    VsAuxiliaryLinks.TERMS_OF_SERVICE,
                    VsAuxiliaryLinks.VULT_TOKEN,
                    VsAuxiliaryLinks.VULT_TOKEN_DOCS,
                    VsAuxiliaryLinks.VULT_WEBSITE,
                )
                .contains(this) || this.startsWith(BANXA_URL)
}
