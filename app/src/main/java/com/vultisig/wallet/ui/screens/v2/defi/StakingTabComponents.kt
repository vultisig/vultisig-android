package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.defi.DefiPositionsUiModel
import com.vultisig.wallet.ui.models.defi.StakePositionUiModel
import com.vultisig.wallet.ui.models.defi.StakingTabUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun StakingTabContent(
    state: DefiPositionsUiModel,
    onClickBond: (String) -> Unit,
    onClickUnbond: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Theme.colors.backgrounds.secondary)
            .border(
                width = 1.dp,
                color = Theme.colors.borders.normal,
                shape = RoundedCornerShape(16.dp)
            )
            .padding(16.dp)
    ) {

    }
}

@Composable
internal fun StakingHeader(
    title: String,
    amount: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = R.drawable.rune),
            contentDescription = null,
            modifier = Modifier.size(36.dp)
        )

        UiSpacer(12.dp)

        Column {
            Text(
                text = "Staked",
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.extraLight,
            )

            UiSpacer(4.dp)

            Text(
                text = "2000",
                style = Theme.brockmann.headings.title1,
                color = Theme.colors.text.primary,
            )
        }
    }
}

// Preview Functions
@Preview(showBackground = true, name = "Staking Tab - Empty State")
@Composable
private fun StakingTabContentEmptyPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        StakingTabContent(
            state = DefiPositionsUiModel(
                staking = StakingTabUiModel(
                    isLoading = false,
                    positions = emptyList()
                )
            ),
            onClickBond = {},
            onClickUnbond = {}
        )
    }
}

@Preview(showBackground = true, name = "Staking Tab - Loading State")
@Composable
private fun StakingTabContentLoadingPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        StakingTabContent(
            state = DefiPositionsUiModel(
                staking = StakingTabUiModel(
                    isLoading = true,
                    positions = emptyList()
                )
            ),
            onClickBond = {},
            onClickUnbond = {}
        )
    }
}

@Preview(showBackground = true, name = "Staking Tab - With Multiple Positions")
@Composable
private fun StakingTabContentWithPositionsPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        StakingTabContent(
            state = DefiPositionsUiModel(
                staking = StakingTabUiModel(
                    isLoading = false,
                    positions = listOf(
                        StakePositionUiModel(
                            stakeAmount = "1000 ATOM",
                            apr = "18.5%",
                            canWithdraw = false,
                            canStake = true,
                            canUnstake = true,
                            rewards = "50 ATOM",
                            nextReward = "5 ATOM",
                            nextPayout = "Jan 15, 2025"
                        ),
                        StakePositionUiModel(
                            stakeAmount = "500 OSMO",
                            apr = "22.3%",
                            canWithdraw = true,
                            canStake = false,
                            canUnstake = false,
                            rewards = "25 OSMO",
                            nextReward = null,
                            nextPayout = "Available Now"
                        ),
                        StakePositionUiModel(
                            stakeAmount = "2500 KUJI",
                            apr = "15.7%",
                            canWithdraw = false,
                            canStake = true,
                            canUnstake = false,
                            rewards = "100 KUJI",
                            nextReward = "10 KUJI",
                            nextPayout = "Feb 1, 2025"
                        )
                    )
                )
            ),
            onClickBond = {},
            onClickUnbond = {}
        )
    }
}

@Preview(showBackground = true, name = "Staking Header - ATOM")
@Composable
private fun StakingHeaderAtomPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        StakingHeader(
            title = "Total Staked ATOM",
            amount = "5000 ATOM"
        )
    }
}

@Preview(showBackground = true, name = "Staking Header - Large Amount")
@Composable
private fun StakingHeaderLargeAmountPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        StakingHeader(
            title = "Total Staked Value",
            amount = "1,234,567.89 USD"
        )
    }
}

@Preview(showBackground = true, name = "Staking Header - Zero Amount")
@Composable
private fun StakingHeaderZeroAmountPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        StakingHeader(
            title = "Total Staked",
            amount = "0 RUNE"
        )
    }
}