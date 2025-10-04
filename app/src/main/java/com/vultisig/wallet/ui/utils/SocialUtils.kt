package com.vultisig.wallet.ui.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

internal object SocialUtils {
    fun openTwitter(context: Context, twitterHandle: String) {
        val twitterPackage = "com.twitter.android"
        val twitterUri = "twitter://user?screen_name=$twitterHandle".toUri()
        val webUrl = "https://twitter.com/$twitterHandle".toUri()

        val pm: PackageManager = context.packageManager
        val intent = if (isAppInstalled(pm, twitterPackage)) {
            Intent(Intent.ACTION_VIEW, twitterUri)
        } else {
            Intent(Intent.ACTION_VIEW, webUrl)
        }

        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    private fun isAppInstalled(pm: PackageManager, packageName: String): Boolean {
        return try {
            pm.getPackageInfo(packageName, PackageManager.GET_ACTIVITIES)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }
}