package com.vultisig.wallet.ui.screens.v2.home.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.animatePlacementInScope
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CorerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun SearchBar(
    modifier: Modifier = Modifier,
    onTNFTsClick: () -> Unit = {},
    onPortfolioClick: () -> Unit = {},
) {
    var state by remember { mutableIntStateOf(0) }
    var tab1WidthDp by remember {
        mutableStateOf(0.dp)
    }
    var tab2WidthDp by remember {
        mutableStateOf(0.dp)
    }

    val underLineWidth = if (state == 0) tab1WidthDp else tab2WidthDp

    val animateWidth by animateDpAsState(underLineWidth)


    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {

        Column(
            modifier = Modifier
        ) {

            UiSpacer(
                size = 6.dp
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.Start
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                VsHomepageTab(
                    onGlobalLayout = {
                        tab1WidthDp = it
                    },
                    onClick = {
                        state = 0
                        onPortfolioClick()
                    },
                    label = "Portfolio",
                    isEnabled = true
                )

                VsHomepageTab(
                    onGlobalLayout = {
                        tab2WidthDp = it
                    },
                    onClick = {
                        state = 1
                        onTNFTsClick()
                    },
                    label = "TNFTs",
                    isEnabled = false
                )

            }

            UiSpacer(
                size = 6.dp
            )

            TabUnderLine(animateWidth, state)
        }

        UiSpacer(weight = 1f)

        Row(
            horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.End),
            verticalAlignment = Alignment.CenterVertically,

            ) {
            V2Container(
                cornerType = CorerType.Circular,
                type = com.vultisig.wallet.ui.components.v2.containers.ContainerType.SECONDARY,
                borderType = com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType.Borderless
            ) {
                UiIcon(
                    drawableResId = R.drawable.ic_search,
                    size = 16.dp,
                    tint = Theme.colors.primary.accent4,
                    modifier = Modifier.padding(12.dp)
                )
            }

            V2Container(
                cornerType = CorerType.Circular,
                type = com.vultisig.wallet.ui.components.v2.containers.ContainerType.SECONDARY,
                borderType = com.vultisig.wallet.ui.components.v2.containers.ContainerBorderType.Borderless
            ) {
                UiIcon(
                    drawableResId = R.drawable.ic_search,
                    size = 16.dp,
                    tint = Theme.colors.primary.accent4,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }


}

@Composable
private fun ColumnScope.TabUnderLine(
    width: Dp,
    state: Int
) {
    LookaheadScope {
        HorizontalDivider(
            modifier = Modifier
                .animatePlacementInScope(this@LookaheadScope)
                .width(width)
                .align(if (state == 0) Alignment.Start else Alignment.End),
            color = Theme.colors.primary.accent4,
            thickness = 1.5.dp
        )
    }
}

@Composable
private fun VsHomepageTab(
    modifier: Modifier = Modifier,
    onGlobalLayout: (Dp) -> Unit,
    onClick: () -> Unit,
    label: String,
    isEnabled: Boolean,
) {
    val density = LocalDensity.current
    Row(
        modifier = modifier
            .clickable(
                onClick = onClick,
                enabled = isEnabled,
            )
            .onGloballyPositioned { coordinates ->
                onGlobalLayout(with(density) { coordinates.size.width.toDp() })
            },
    ) {
        Text(
            text = label,
            color = if (isEnabled) Theme.colors.text.primary else Theme.colors.text.button.disabled,
            style = Theme.brockmann.body.s.medium
        )

        if(isEnabled.not()){

            Spacer(modifier = Modifier.width(6.dp))

            V2Container(
                type = ContainerType.SECONDARY,
                cornerType = CorerType.RoundedCornerShape(8.dp),
            ) {
                Text(
                    text = "Soon",
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
private fun PreviewSearchBar() {
    SearchBar()
}


