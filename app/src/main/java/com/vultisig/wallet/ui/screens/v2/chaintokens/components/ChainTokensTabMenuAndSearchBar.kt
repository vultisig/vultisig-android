package com.vultisig.wallet.ui.screens.v2.chaintokens.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.tab.VsTab
import com.vultisig.wallet.ui.components.v2.tab.VsTabGroup

@Composable
fun ChainTokensTabMenuAndSearchBar(
    modifier: Modifier = Modifier
) {

    var tabIndex by remember {
        mutableIntStateOf(0)
    }

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
                            tabIndex = 0
                        }
                    )
                },
                {
                    VsTab(
                        label = "Hidden",
                        onClick = {
                            tabIndex = 1
                        }
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
        ) {
            UiIcon(
                drawableResId = com.vultisig.wallet.R.drawable.ic_search,
                onClick = {},
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
        ) {
            UiIcon(
                drawableResId = com.vultisig.wallet.R.drawable.ic_search,
                onClick = {},
                size = 16.dp,
                modifier = Modifier
                    .padding(
                        all = 8.dp
                    )
            )
        }


    }
}

@Preview
@Composable
private fun PreviewChainTokensTabMenuAndSearchBar() {
    ChainTokensTabMenuAndSearchBar()
}