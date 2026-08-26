package com.vultisig.wallet.ui.screens.v2.home.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.vultisig.wallet.ui.screens.v2.home.pager.HomepagePager
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.HomeBannerType
import com.vultisig.wallet.ui.utils.SocialUtils
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks

@Composable
internal fun Banners(
    banners: List<HomeBannerType>,
    onBannerClick: (HomeBannerType) -> Unit,
    onBannerDismiss: (HomeBannerType) -> Unit,
    context: Context,
    modifier: Modifier = Modifier,
) {
    HomepagePager(
        modifier = modifier,
        banners = banners,
        // Follow X leaves the app rather than routing, so it is answered here where a Context is in
        // scope instead of in the ViewModel; every other banner is a navigation the ViewModel owns.
        onBannerClick = { banner ->
            if (banner == HomeBannerType.FollowX) {
                SocialUtils.openTwitter(
                    context = context,
                    twitterHandle = VsAuxiliaryLinks.TWITTER_ID,
                )
            } else {
                onBannerClick(banner)
            }
        },
        onBannerDismiss = onBannerDismiss,
    )
}
