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
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.defi.StakePositionUiModel
import com.vultisig.wallet.ui.models.defi.StakingTabUiModel
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun StakingTabContent(
    state: StakingTabUiModel,
    onClickStake: () -> Unit,
    onClickUnstake: () -> Unit,
    onClickWithdraw: () -> Unit,
) {
    state.positions.forEach { stakingPosition ->
        StakingWidget(
            state = stakingPosition,
            onClickStake = {},
            onClickUnstake = {},
            onClickWithdraw = {}
        )
    }
}

@Composable
internal fun StakingWidget(
    state: StakePositionUiModel,
    onClickStake: () -> Unit,
    onClickUnstake: () -> Unit,
    onClickWithdraw: () -> Unit,
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
        StakingHeader(
            title = state.stakeAssetHeader,
            amount = state.stakeAmount,
            icon = getHeaderIcon(state.stakeAmount),
        )

        UiSpacer(16.dp)

        UiHorizontalDivider(color = Theme.v2.colors.border.light)

        UiSpacer(16.dp)

        ApyInfoItem(
            apy = state.apy
        )
    }
}

@Composable
internal fun StakingHeader(
    title: String,
    amount: String,
    icon: Int,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(id = icon),
            contentDescription = null,
            modifier = Modifier.size(46.dp)
        )

        UiSpacer(12.dp)

        Column {
            Text(
                text = title,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.extraLight,
            )

            UiSpacer(4.dp)

            Text(
                text = amount,
                style = Theme.brockmann.headings.title1,
                color = Theme.colors.text.primary,
            )
        }
    }
}

private fun getHeaderIcon(assetStake: String): Int {
    return when {
        assetStake.contains("ruji", ignoreCase = true) -> R.drawable.ruji_staking
        assetStake.contains("tcy", ignoreCase = true) -> R.drawable.tcy_staking
        else -> R.drawable.wewe
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
            title = "Total Staked TCY",
            amount = "5000 ATOM",
            icon = R.drawable.tcy_staking,
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
            amount = "1,234,567.89 RUJI",
            icon = R.drawable.ruji_staking,
        )
    }
}

@Preview(showBackground = true, name = "Staking Widget - Can Stake & Unstake")
@Composable
private fun StakingWidgetFullActionsPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        StakingWidget(
            state = StakePositionUiModel(
                stakeAssetHeader = "Staked RUJI",
                stakeAmount = "1000 RUJI",
                apy = "18.5%",
                canWithdraw = false,
                canStake = true,
                canUnstake = true,
                rewards = "50 RUJI",
                nextReward = "5 RUJI",
                nextPayout = "Jan 15, 2025"
            ),
            onClickStake = {},
            onClickUnstake = {},
            onClickWithdraw = {}
        )
    }
}