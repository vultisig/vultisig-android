package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun HomePageTabMenu(
    modifier: Modifier = Modifier,
//    onTNFTsClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
    onEditClick: () -> Unit = {},
    onSearchClick: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        var state by remember {
            mutableIntStateOf(0)
        }

        VsTabGroup(
            index = state,
            tabs = listOf(
                {
                    VsTab(
                        label = stringResource(R.string.search_bar_portfolio),
                        onClick = {
                            onPortfolioClick()
                            state = 0
                        },
                        isEnabled = true
                    )
                },
//                {
//                    HomepageTab(
//                        onClick = {
//                            onTNFTsClick()
//                            state = 1
//                        },
//                        label = stringResource(R.string.search_bar_nfts),
//                        isEnabled = false
//                    )
//                },
            ),
        )

        UiSpacer(weight = 1f)

        Row(
            horizontalArrangement = Arrangement.spacedBy(
                space = 8.dp,
                alignment = Alignment.End
            ),
            verticalAlignment = Alignment.CenterVertically,

            ) {
            V2Container(
                modifier = Modifier.clickOnce(onClick = onSearchClick),
                cornerType = CornerType.Circular,
                type = ContainerType.SECONDARY,
                borderType = ContainerBorderType.Borderless
            ) {
                UiIcon(
                    drawableResId = R.drawable.ic_search,
                    size = 16.dp,
                    tint = Theme.colors.text.primary,
                    modifier = Modifier.padding(12.dp)
                )
            }

            V2Container(
                modifier = Modifier.clickOnce(onClick = onEditClick),
                cornerType = CornerType.Circular,
                type = ContainerType.SECONDARY,
                borderType = ContainerBorderType.Borderless
            ) {
                UiIcon(
                    drawableResId = R.drawable.edit_chain,
                    size = 16.dp,
                    tint = Theme.colors.primary.accent4,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}


@Composable
private fun HomepageTab(
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    label: String,
    isEnabled: Boolean,
) {
    Row(
        modifier = modifier
    ) {

        VsTab(
            label = label,
            onClick = onClick,
            isEnabled = isEnabled,
        )

        if (isEnabled.not()) {

            Spacer(modifier = Modifier.width(6.dp))

            V2Container(
                type = ContainerType.SECONDARY,
                cornerType = CornerType.RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.search_bar_soon),
                    color = Theme.colors.alerts.info,
                    style = Theme.brockmann.supplementary.caption,
                    modifier = Modifier.padding(
                        horizontal = 6.dp,
                        vertical = 4.dp
                    )
                )
            }
        }

    }
}


@Preview
@Composable
private fun PreviewHomePageTabMenu() {
    HomePageTabMenu()
}