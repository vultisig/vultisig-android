package com.vultisig.wallet.ui.screens.v2.home.components

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.screens.v2.home.pager.HomepagePager
import com.vultisig.wallet.ui.screens.v2.home.pager.HomepagePagerParams
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.SocialUtils
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks

@Composable
internal fun Banners(
    hasMigration: Boolean,
    onMigrateClick: () -> Unit,
    context: Context,
    onDismissBanner: () -> Unit,
) {
    HomepagePager(
        modifier = Modifier.Companion
            .padding(all = 16.dp),
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
    UiSpacer(20.dp)
    UiHorizontalDivider(
        color = Theme.colors.borders.light,
        modifier = Modifier.Companion.padding(horizontal = 16.dp)
    )
}