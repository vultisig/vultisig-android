package com.vultisig.wallet.ui.screens.v2.chaintokens.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.tab.TabMenuAndSearchBar
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup

@Composable
fun ChainTokensTabMenuAndSearchBar(
    modifier: Modifier = Modifier,
    isTabMenu: Boolean,
    searchTextFieldState: TextFieldState,
    onCancelSearchClick: () -> Unit,
    onEditClick: () -> Unit,
    onSearchClick: () -> Unit,
    onHiddenClick: () -> Unit,
    onTokensClick: () -> Unit,
) {

    var tabIndex by remember {
        mutableIntStateOf(0)
    }

    TabMenuAndSearchBar(
        modifier = modifier,
        isTabMenu = isTabMenu,
        searchTextFiledState = searchTextFieldState,
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
                                label = "Tokens",
                                onClick = {
                                    onTokensClick()
                                    tabIndex = 0
                                }
                            )
                        },
                        {
                            VsTab(
                                label = "Hidden",
                                onClick = {
                                    onHiddenClick()
                                    tabIndex = 1
                                },
                                isEnabled = false
                            )
                        }
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
                        drawableResId = com.vultisig.wallet.R.drawable.ic_search,
                        size = 16.dp,
                        modifier = Modifier
                            .padding(
                                all = 8.dp
                            )
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
                        drawableResId = com.vultisig.wallet.R.drawable.edit,
                        size = 16.dp,
                        modifier = Modifier
                            .padding(
                                all = 8.dp
                            )
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
        onHiddenClick = {},
        onTokensClick = {},
        onCancelSearchClick = {},
        searchTextFieldState = rememberTextFieldState(),
        isTabMenu = true
    )
}