package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.containers.ContainerType
import com.vultisig.wallet.ui.components.v2.containers.CornerType
import com.vultisig.wallet.ui.components.v2.containers.V2Container
import com.vultisig.wallet.ui.components.v2.scaffold.V2Scaffold
import com.vultisig.wallet.ui.models.defi.DefiPositionsViewModel
import com.vultisig.wallet.ui.models.defi.DefiPositionsUiModel
import com.vultisig.wallet.ui.screens.v2.defi.model.BondNodeState
import com.vultisig.wallet.ui.screens.v2.home.components.VsTabs
import com.vultisig.wallet.ui.screens.v2.home.components.NotEnabledContainer
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun DefiPositionsScreen(
    model: DefiPositionsViewModel = hiltViewModel<DefiPositionsViewModel>(),
) {
    val state by model.state.collectAsState()

    DefiPositionScreenContent(
        state = state,
        onBackClick = model::onBackClick,
        onClickBondToNode = model::bondToNode,
        onClickUnbond = { model.onClickUnBond(it) },
        onClickBond = { model.onClickBond(it) },
    )
}

@Composable
internal fun DefiPositionScreenContent(
    state: DefiPositionsUiModel = DefiPositionsUiModel(),
    onBackClick: () -> Unit,
    onClickBondToNode: () -> Unit,
    onClickUnbond: (String) -> Unit,
    onClickBond: (String) -> Unit,
) {
    val tabs = listOf(BONDED_TAB, STAKING_TAB, LPs_TAB)
    var selectedTab by remember { mutableStateOf(tabs.first()) }

    V2Scaffold(
        onBackClick = onBackClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.backgrounds.primary),
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BalanceBanner(
                isLoading = state.bonded.isLoading,
                totalValue = state.totalAmountPrice
            )

            VsTabs(
                tabs = listOf(BONDED_TAB, STAKING_TAB, LPs_TAB),
                onTabSelected = { selectedTab = it },
                selectedTab = selectedTab,
                content = {
                    V2Container(
                        type = ContainerType.SECONDARY,
                        cornerType = CornerType.Circular,
                        modifier = Modifier
                            .clickOnce(onClick = {} )
                    ) {
                        UiIcon(
                            drawableResId = R.drawable.edit_chain,
                            size = 16.dp,
                            modifier = Modifier.padding(all = 12.dp),
                            tint = Theme.colors.primary.accent4,
                        )
                    }
                }
            )

            when (selectedTab) {
                BONDED_TAB -> {
                    BondedTabContent(
                        bondToNodeOnClick = onClickBondToNode,
                        state = state,
                        onClickUnbond = onClickUnbond,
                        onClickBond = onClickBond,
                    )
                }
                STAKING_TAB -> {

                }
                LPs_TAB -> {
                    NoPositionsContainer(
                        onManagePositionsClick = { }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoPositionsContainer(
    onManagePositionsClick: () -> Unit = {}
) {
    NotEnabledContainer(
        title = stringResource(R.string.defi_no_positions_selected),
        content = stringResource(R.string.defi_no_positions_selected_desc),
        action = {
            Text(
                text = stringResource(R.string.manage_positions),
                style = Theme.brockmann.button.medium.medium,
                color = Theme.colors.text.primary,
                modifier = Modifier
                    .clip(shape = CircleShape)
                    .clickOnce(onClick = onManagePositionsClick)
                    .background(
                        color = Theme.v2.colors.border.primaryAccent4
                    )
                    .padding(
                        vertical = 8.dp,
                        horizontal = 16.dp
                    )
            )
        }
    )
}

@Composable
private fun BalanceBanner(
    isLoading: Boolean,
    totalValue: String,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                color = Theme.colors.borders.light,
                shape = RoundedCornerShape(16.dp)
            )
    ) {
        Image(
            painter = painterResource(id = R.drawable.referral_data_banner),
            contentDescription = "Provider Logo",
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize()
        )

        Column(
            modifier = Modifier
                .padding(start = 16.dp, top = 16.dp)
        ) {
            Text(
                text = Chain.ThorChain.name,
                color = Theme.colors.text.primary,
                style = Theme.brockmann.body.l.medium,
            )

            UiSpacer(16.dp)

            Text(
                text = stringResource(R.string.defi_balance),
                color = Theme.colors.text.primary,
                style = Theme.brockmann.supplementary.caption,
            )

            UiSpacer(12.dp)

            if (isLoading) {
                UiPlaceholderLoader(
                    modifier = Modifier
                        .size(width = 150.dp, height = 32.dp)
                )
            } else {
                Text(
                    text = totalValue,
                    color = Theme.colors.text.primary,
                    style = Theme.satoshi.price.title1,
                )
            }
        }
    }
}

@Composable
@Preview(showBackground = true, name = "DeFi Positions - Empty")
private fun DefiPositionsScreenPreviewEmpty() {
    DefiPositionScreenContent(
        onBackClick = { },
        state = DefiPositionsUiModel(),
        onClickBond = {},
        onClickUnbond = {},
        onClickBondToNode = {}
    )
}

@Composable
@Preview(showBackground = true, name = "DeFi Positions - With Data")
private fun DefiPositionsScreenPreviewWithData() {
    val mockNodes = listOf(
        com.vultisig.wallet.ui.models.defi.BondedNodeUiModel(
            address = "thor1abcd...xyz",
            status = BondNodeState.ACTIVE,
            apy = "12.5%",
            bondedAmount = "1000 RUNE",
            nextAward = "20 RUNE",
            nextChurn = "Oct 15, 25"
        ),
        com.vultisig.wallet.ui.models.defi.BondedNodeUiModel(
            address = "thor1efgh...123",
            status = BondNodeState.STANDBY,
            apy = "11.2%",
            bondedAmount = "500 RUNE",
            nextAward = "10 RUNE",
            nextChurn = "Oct 16, 25"
        ),
        com.vultisig.wallet.ui.models.defi.BondedNodeUiModel(
            address = "thor1ijkl...456",
            status = BondNodeState.READY,
            apy = "10.8%",
            bondedAmount = "750 RUNE",
            nextAward = "15 RUNE",
            nextChurn = "Oct 17, 25"
        )
    )
    
    DefiPositionScreenContent(
        onBackClick = { },
        state = DefiPositionsUiModel(
            totalAmountPrice = "$3,250.00",
            selectedTab = BONDED_TAB,
            bonded = com.vultisig.wallet.ui.models.defi.BondedTabUiModel(
                isLoading = false,
                totalBondedAmount = "2250 RUNE",
                nodes = mockNodes
            )
        ),
        onClickBond = {},
        onClickUnbond = {},
        onClickBondToNode = {}
    )
}

@Composable
@Preview(showBackground = true, name = "DeFi Positions - Loading")
private fun DefiPositionsScreenPreviewLoading() {
    DefiPositionScreenContent(
        onBackClick = { },
        state = DefiPositionsUiModel(
            bonded = com.vultisig.wallet.ui.models.defi.BondedTabUiModel(
                isLoading = true
            )
        ),
        onClickBond = {},
        onClickUnbond = {},
        onClickBondToNode = {}
    )
}

internal const val BONDED_TAB = "Bonded"
internal const val STAKING_TAB = "Staked"
internal const val LPs_TAB = "LPs"