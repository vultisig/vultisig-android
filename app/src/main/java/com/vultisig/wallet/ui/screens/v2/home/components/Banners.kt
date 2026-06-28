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
    showUpgrade: Boolean,
    showFollowX: Boolean,
    showBuyVult: Boolean,
    onMigrateClick: () -> Unit,
    onBuyVultClick: () -> Unit,
    onBuyVultDismiss: () -> Unit,
    context: Context,
    onUpgradeDismiss: () -> Unit,
    onFollowXDismiss: () -> Unit,
) {
    HomepagePager(
        modifier = modifier,
        params =
            HomepagePagerParams(
                showUpgrade = showUpgrade,
                showFollowX = showFollowX,
                showBuyVult = showBuyVult,
            ),
        onUpgradeClick = onMigrateClick,
        onFollowXClick = {
            SocialUtils.openTwitter(context = context, twitterHandle = VsAuxiliaryLinks.TWITTER_ID)
        },
        onBuyVultClick = onBuyVultClick,
        onBuyVultDismiss = onBuyVultDismiss,
        onUpgradeDismiss = onUpgradeDismiss,
        onFollowXDismiss = onFollowXDismiss,
    )
}
