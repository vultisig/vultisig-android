package com.vultisig.wallet.ui.screens.v2.home.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vultisig.wallet.ui.screens.v2.home.pager.HomepagePager
import com.vultisig.wallet.ui.screens.v2.home.pager.HomepagePagerParams
import com.vultisig.wallet.ui.utils.SocialUtils
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks

@Composable
internal fun Banners(
    modifier: Modifier,
    hasMigration: Boolean,
    onMigrateClick: () -> Unit,
    context: Context,
    onDismissBanner: () -> Unit,
) {
    HomepagePager(
        modifier = modifier,
        params = HomepagePagerParams(
            hasMigration = hasMigration
        ),
        onUpgradeClick = onMigrateClick,
        onFollowXClick = {
            SocialUtils.openTwitter(
                context = context,
                twitterHandle = VsAuxiliaryLinks.TWITTER_ID
            )
        },
        onCloseClick = onDismissBanner,
    )
}