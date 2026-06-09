package com.vultisig.wallet.ui.utils

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService
import timber.log.Timber

internal fun Activity.openCct(uri: Uri, onError: () -> Unit = {}) {
    val intent = CustomTabsIntent.Builder().build()
    val cctPackageName = getCustomTabsPackages().getOrNull(0)?.activityInfo?.packageName
    if (!cctPackageName.isNullOrBlank()) {
        intent.intent.setPackage(cctPackageName)
        intent.launchUrl(this, uri)
    } else {
        openInBrowser(uri, onError)
    }
}

/**
 * Opens [uri] in a standard browser via [Intent.ACTION_VIEW].
 *
 * Used as a fallback when no Custom-Tabs-capable browser is installed (e.g. Chrome-less devices
 * with Firefox as the default browser). [onError] is invoked only if no browser can handle the URI.
 *
 * @param uri the URL to open.
 * @param onError invoked when no activity is available to open the URL.
 */
private fun Activity.openInBrowser(uri: Uri, onError: () -> Unit) {
    try {
        val browserIntent = Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)
        startActivity(browserIntent)
    } catch (e: ActivityNotFoundException) {
        Timber.e(e, "No browser available to open URI: %s", uri)
        onError()
    }
}

private fun Activity.getCustomTabsPackages(): ArrayList<ResolveInfo> {
    val pm = packageManager
    val activityIntent =
        Intent()
            .setAction(Intent.ACTION_VIEW)
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .setData(Uri.fromParts("https", "", null))
    val resolvedActivityList = pm.queryIntentActivities(activityIntent, 0)
    val packagesSupportingCustomTabs = ArrayList<ResolveInfo>()
    for (info in resolvedActivityList) {
        val serviceIntent = Intent()
        serviceIntent.action = CustomTabsService.ACTION_CUSTOM_TABS_CONNECTION
        serviceIntent.setPackage(info.activityInfo.packageName)
        if (pm.resolveService(serviceIntent, 0) != null) {
            packagesSupportingCustomTabs.add(info)
        }
    }
    return packagesSupportingCustomTabs
}
