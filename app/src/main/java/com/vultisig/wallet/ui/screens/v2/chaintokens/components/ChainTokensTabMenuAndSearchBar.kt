package com.vultisig.wallet.ui.screens.v2.chaintokens.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
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
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.tab.TabMenuAndSearchBar
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun ChainTokensTabMenuAndSearchBar(
    modifier: Modifier = Modifier,
    isTabMenu: Boolean,
    searchTextFieldState: TextFieldState,
    onCancelSearchClick: () -> Unit,
    onEditClick: () -> Unit,
    onSearchClick: () -> Unit,
    onTokensClick: () -> Unit,
) {

    var tabIndex by remember {
        mutableIntStateOf(0)
    }

    TabMenuAndSearchBar(
        modifier = modifier,
        isTabMenu = isTabMenu,
        searchTextFieldState = searchTextFieldState,
        isInitiallyFocused = true,
        tabMenuContent = {
            Row(
                modifier = modifier
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                VsTabGroup(
                    tabs = listOf(
                        {
                            VsTab(
                                label = stringResource(R.string.tokens),
                                onClick = {
                                    onTokensClick()
                                    tabIndex = 0
                                }
                            )
                        },
                    ),
                    index = tabIndex
                )

                UiSpacer(
                    weight = 1f
                )

                V2Container(
                    type = ContainerType.SECONDARY,
                    cornerType = CornerType.Circular,
                    modifier = Modifier
                        .clickOnce(onClick = onSearchClick)
                ) {
                    UiIcon(
                        drawableResId = R.drawable.ic_search,
                        size = 16.dp,
                        modifier = Modifier.padding(all = 12.dp)
                    )
                }

                UiSpacer(
                    size = 8.dp
                )

                V2Container(
                    type = ContainerType.SECONDARY,
                    cornerType = CornerType.Circular,
                    modifier = Modifier
                        .clickOnce(onClick = onEditClick)
                ) {
                    UiIcon(
                        drawableResId = R.drawable.edit_chain,
                        size = 16.dp,
                        modifier = Modifier.padding(all = 12.dp),
                        tint = Theme.colors.primary.accent4,
                    )
                }
            }
        },
        onCancelSearchClick = onCancelSearchClick
    )


}

@Preview
@Composable
private fun PreviewChainTokensTabMenuAndSearchBar() {
    ChainTokensTabMenuAndSearchBar(
        onEditClick = {},
        onSearchClick = {},
        onTokensClick = {},
        onCancelSearchClick = {},
        searchTextFieldState = rememberTextFieldState(),
        isTabMenu = true
    )
}