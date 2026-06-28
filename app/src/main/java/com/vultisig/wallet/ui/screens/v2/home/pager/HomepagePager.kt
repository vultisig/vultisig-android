package com.vultisig.wallet.ui.screens.v2.home.pager

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.pager.VsPager
import com.vultisig.wallet.ui.components.v2.pager.indicator.VsPagerIndicator
import com.vultisig.wallet.ui.components.v2.pager.utils.rememberVsPagerState
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.BuyVultBanner
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.FollowXBanner
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.UpgradeBanner
import com.vultisig.wallet.ui.screens.v2.home.pager.container.HomePagePagerContainer

@Composable
internal fun HomepagePager(
    modifier: Modifier = Modifier,
    params: HomepagePagerParams,
    onUpgradeClick: () -> Unit,
    onFollowXClick: () -> Unit,
    onBuyVultClick: () -> Unit,
    onBuyVultDismiss: () -> Unit,
    onUpgradeDismiss: () -> Unit,
    onFollowXDismiss: () -> Unit,
) {
    val state = rememberVsPagerState(key = params)

    Column(
        modifier = modifier.animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VsPager(state = state) {
            if (params.showUpgrade)
                item {
                    HomePagePagerContainer(onCloseClick = onUpgradeDismiss) {
                        UpgradeBanner(onUpgradeClick = onUpgradeClick)
                    }
                }

            if (params.showFollowX)
                item {
                    HomePagePagerContainer(onCloseClick = onFollowXDismiss) {
                        FollowXBanner(onFollowXClick = onFollowXClick)
                    }
                }

            if (params.showBuyVult)
                item {
                    HomePagePagerContainer(onCloseClick = onBuyVultDismiss) {
                        BuyVultBanner(onBuyVultClick = onBuyVultClick)
                    }
                }
        }

        if (state.pageCount > 1) {

            UiSpacer(size = 12.dp)

            VsPagerIndicator(
                selectedPage = state.currentPage,
                numberOfPages = state.pageCount,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Preview
@Composable
private fun HomepagePagerPreview() {
    HomepagePager(
        params = HomepagePagerParams(showUpgrade = true, showFollowX = true, showBuyVult = true),
        onUpgradeClick = {},
        onFollowXClick = {},
        onBuyVultClick = {},
        onBuyVultDismiss = {},
        onUpgradeDismiss = {},
        onFollowXDismiss = {},
    )
}

internal data class HomepagePagerParams(
    val showUpgrade: Boolean,
    val showFollowX: Boolean,
    val showBuyVult: Boolean,
)
