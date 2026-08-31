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
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.HomeBanner
import com.vultisig.wallet.ui.screens.v2.home.pager.banner.HomeBannerType

@Composable
internal fun HomepagePager(
    banners: List<HomeBannerType>,
    onBannerClick: (HomeBannerType) -> Unit,
    onBannerDismiss: (HomeBannerType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberVsPagerState(key = banners)

    Column(
        modifier = modifier.animateContentSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        VsPager(state = state) {
            banners.forEach { banner ->
                item {
                    HomeBanner(
                        banner = banner,
                        onClick = { onBannerClick(banner) },
                        onCloseClick = { onBannerDismiss(banner) },
                    )
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
    HomepagePager(banners = HomeBannerType.entries, onBannerClick = {}, onBannerDismiss = {})
}
