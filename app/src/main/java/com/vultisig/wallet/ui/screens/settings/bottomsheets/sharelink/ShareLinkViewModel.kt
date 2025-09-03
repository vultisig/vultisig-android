package com.vultisig.wallet.ui.screens.settings.bottomsheets.sharelink

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Drawable
import androidx.appcompat.content.res.AppCompatResources
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

internal data class ShareOptionUiModel(
    val packageName: String,
    val activityName: String? = null,
    val label: String,
    val icon: Drawable,
    val isSpecial: Boolean = false // for "More" option
)

internal data class ShareLinkUiModel(
    val link: String = "",
    val shareOptions: List<ShareOptionUiModel> = emptyList()
)


@HiltViewModel
internal class ShareLinkViewModel @Inject constructor() : ViewModel() {

    fun getUiModel(context: Context) = ShareLinkUiModel(
        link = SHARE_LINK,
        shareOptions = getAvailableShareApps(context)
    )

    private fun getAvailableShareApps(context: Context): List<ShareOptionUiModel> {
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
        }

        val packageManager = context.packageManager
        val activities = packageManager.queryIntentActivities(shareIntent, 0)

        return activities.map { resolveInfo ->
            ShareOptionUiModel(
                packageName = resolveInfo.activityInfo.packageName,
                activityName = resolveInfo.activityInfo.name,
                label = resolveInfo.loadLabel(packageManager).toString(),
                icon = resolveInfo.loadIcon(packageManager)
            )
        } + ShareOptionUiModel(
            packageName = "",
            label = "More",
            icon = AppCompatResources.getDrawable(
                context,
                R.drawable.plus
            )!!,
            isSpecial = true
        )
    }

    fun onShareClick(shareOption: ShareOptionUiModel, context: Context) {
        if (shareOption.isSpecial) {
            context.shareAppLink()
        } else {
            context.shareViaSpecificApp(shareOption)
        }
    }


    private fun Context.shareAppLink() {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(
                Intent.EXTRA_TEXT,
                SHARE_LINK
            )
            type = "text/plain"
        }
        val shareIntent = Intent.createChooser(
            sendIntent,
            null
        )
        startActivity(shareIntent)
    }

    private fun Context.shareViaSpecificApp(shareOption: ShareOptionUiModel) {
        val intent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, SHARE_LINK)
            type = "text/plain"
            if (shareOption.activityName != null) {
                component =
                    ComponentName(shareOption.packageName, shareOption.activityName)
            } else {
                setPackage(shareOption.packageName)
            }
        }

        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            shareAppLink()
        }
    }

    companion object {
        private const val SHARE_LINK = VsAuxiliaryLinks.GOOGLE_PLAY
    }
}