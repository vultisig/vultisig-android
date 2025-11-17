package com.vultisig.wallet.ui.utils

import android.app.Activity
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsService

internal fun Activity.openCct(uri: Uri, onError: () -> Unit = {}) {
    val intent = CustomTabsIntent.Builder().build()
    val cctPackageName = getCustomTabsPackages().getOrNull(0)?.activityInfo?.packageName
    if (!cctPackageName.isNullOrBlank()) {
        intent.intent.setPackage(cctPackageName)
        intent.launchUrl(this, uri)
    } else {
        onError()
    }
}

private fun Activity.getCustomTabsPackages(): ArrayList<ResolveInfo> {
    val pm = packageManager
    val activityIntent = Intent()
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