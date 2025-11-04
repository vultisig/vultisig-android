package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
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
            isLoading = state.isLoading,
            onClickStake = onClickStake,
            onClickUnstake = onClickUnstake,
            onClickWithdraw = onClickWithdraw,
        )
    }
}

@Composable
internal fun StakingWidget(
    state: StakePositionUiModel,
    isLoading: Boolean = false,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = getHeaderIcon(state.stakeAmount)),
                contentDescription = null,
                modifier = Modifier.size(46.dp)
            )

            UiSpacer(12.dp)

            Column {
                Text(
                    text = state.stakeAssetHeader,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.extraLight,
                )

                UiSpacer(4.dp)

                if (isLoading) {
                    UiPlaceholderLoader(
                        modifier = Modifier
                            .width(120.dp)
                            .height(28.dp)
                    )
                } else {
                    Text(
                        text = state.stakeAmount,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.colors.text.primary,
                    )
                }
            }
        }

        if (state.apy != null || (state.nextReward != null || state.nextPayout != null)) {
            UiSpacer(16.dp)

            UiHorizontalDivider(color = Theme.v2.colors.border.light)

            UiSpacer(16.dp)
        }

        if (state.apy != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                InfoItem(
                    icon = R.drawable.ic_icon_percentage,
                    label = stringResource(R.string.apy),
                    value = null,
                )

                UiSpacer(1f)

                Text(
                    text = state.apy,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.alerts.success,
                )
            }
        }

        if (state.nextPayout != null || state.nextReward != null) {
            UiSpacer(16.dp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (state.nextPayout != null) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    InfoItem(
                        icon = R.drawable.calendar_days,
                        label = stringResource(R.string.next_payout),
                        value = state.nextPayout,
                    )
                }
            }

            if (state.nextReward != null) {
                Box(
                    modifier = Modifier.weight(1f)
                ) {
                    InfoItem(
                        icon = R.drawable.ic_cup,
                        label = stringResource(R.string.next_award),
                        value = state.nextReward,
                    )
                }
            }
        }

        UiSpacer(16.dp)

        UiHorizontalDivider(color = Theme.v2.colors.border.light)

        UiSpacer(16.dp)

        if (state.canWithdraw) {
            VsButton(
                label = stringResource(R.string.withdraw_amount, "300.45 USDC"),
                modifier = Modifier.fillMaxWidth(),
                onClick = onClickWithdraw,
                state = VsButtonState.Enabled,
            )

            UiSpacer(16.dp)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ActionButton(
                title = "Unstake",
                icon = R.drawable.ic_circle_minus,
                background = Color.Transparent,
                border = BorderStroke(1.dp, Theme.v2.colors.primary.accent4),
                contentColor = Theme.v2.colors.text.primary,
                onClick = onClickUnstake,
                modifier = Modifier.weight(1f),
                enabled = state.canUnstake,
                iconCircleColor = Theme.v2.colors.text.extraLight
            )

            ActionButton(
                title = "Stake",
                icon = R.drawable.ic_circle_plus,
                background = Theme.v2.colors.primary.accent3,
                contentColor = Theme.v2.colors.text.primary,
                onClick = onClickStake,
                modifier = Modifier.weight(1f),
                enabled = state.canStake,
                iconCircleColor = Theme.v2.colors.primary.accent4
            )
        }
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
                canWithdraw = true,
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
@Preview(showBackground = true, name = "Staking Widget - Loading State")
@Composable
private fun StakingWidgetLoadingPreview() {
    Box(
        modifier = Modifier
            .background(Theme.colors.backgrounds.primary)
            .padding(16.dp)
    ) {
        StakingWidget(
            state = StakePositionUiModel(
                stakeAssetHeader = "Staked RUJI",
                stakeAmount = "0 RUJI",
                apy = "0%",
                canWithdraw = false,
                canStake = false,
                canUnstake = false,
                rewards = null,
                nextReward = null,
                nextPayout = null
            ),
            isLoading = true,
            onClickStake = {},
            onClickUnstake = {},
            onClickWithdraw = {}
        )
    }
}
