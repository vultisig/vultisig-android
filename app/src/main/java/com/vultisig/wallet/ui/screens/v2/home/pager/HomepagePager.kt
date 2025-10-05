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
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.FollowXBanner
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.UpgradeBanner
import com.vultisig.wallet.ui.screens.v2.home.pager.container.HomePagePagerContainer

@Composable
internal fun HomepagePager(
    modifier: Modifier = Modifier,
    params: HomepagePagerParams,
    onCloseClick: () -> Unit,
    onUpgradeClick: () -> Unit,
    onFollowXClick: () -> Unit,
) {
    val state = rememberVsPagerState(key = params)

    Column(
        modifier = modifier
            .animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        VsPager(state = state) {
            if (params.hasMigration)
                item {
                    HomePagePagerContainer(
                        onCloseClick = onCloseClick
                    ) {
                        UpgradeBanner(
                            onUpgradeClick = onUpgradeClick
                        )
                    }
                }

            item {
                HomePagePagerContainer(
                    onCloseClick = onCloseClick
                ) {
                    FollowXBanner(
                        onFollowXClick = onFollowXClick,
                    )
                }
            }
        }

        if (state.pageCount > 1) {

            UiSpacer(
                size = 12.dp
            )

            VsPagerIndicator(
                selectedPage = state.currentPage,
                numberOfPages = state.pageCount,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}


@Preview
@Composable
private fun HomepagePagerPreview() {
    HomepagePager(
        params = HomepagePagerParams(
            hasMigration = true,
        ),
        onCloseClick = {},
        onUpgradeClick = {},
        onFollowXClick = {},
    )
}

internal data class HomepagePagerParams(
    val hasMigration: Boolean,
)