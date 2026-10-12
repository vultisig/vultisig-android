package com.vultisig.wallet.ui.screens.v2.defi

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.models.defi.BondRewardHistoryUiModel
import com.vultisig.wallet.ui.models.defi.BondRewardRowUiModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Total Rewards Earned for one bonded node: the realised churn payouts summed, the share still
 * accruing toward the next churn, then one row per past churn, newest first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BondRewardHistorySheet(
    model: BondRewardHistoryUiModel,
    @DrawableRes coinIconRes: Int,
    isBalanceVisible: Boolean,
    onDismissRequest: () -> Unit,
) {
    V2BottomSheet(onDismissRequest = onDismissRequest) {
        BondRewardHistoryContent(
            model = model,
            coinIconRes = coinIconRes,
            isBalanceVisible = isBalanceVisible,
        )
    }
}

@Composable
private fun BondRewardHistoryContent(
    model: BondRewardHistoryUiModel,
    @DrawableRes coinIconRes: Int,
    isBalanceVisible: Boolean,
) {
    fun amount(value: String) = if (isBalanceVisible) value else HIDE_BALANCE_CHARS

    LazyColumn(
        modifier =
            Modifier.fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 45.dp)
                .navigationBarsPadding()
                .padding(bottom = 40.dp)
    ) {
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.total_rewards_earned),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.Center,
                )

                when {
                    model.isLoading ->
                        UiPlaceholderLoader(
                            modifier = Modifier.size(width = 150.dp, height = 34.dp)
                        )

                    else ->
                        Text(
                            text =
                                model.totalEarned?.let(::amount)
                                    ?: stringResource(R.string.last_reward_unavailable),
                            style = Theme.brockmann.headings.title1,
                            color = Theme.v2.colors.text.primary,
                            textAlign = TextAlign.Center,
                        )
                }

                Text(
                    text =
                        stringResource(
                            R.string.reward_history_node,
                            model.nodeAddress.formatAddress(),
                        ),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.Center,
                )
            }

            UiSpacer(20.dp)

            UiHorizontalDivider(color = Theme.v2.colors.border.light)
        }

        item {
            RewardRow(
                coinIconRes = coinIconRes,
                amount = "~${amount(model.upcoming)}",
                badge = stringResource(R.string.reward_history_upcoming),
                badgeColor = Theme.v2.colors.alerts.warning,
            )
        }

        when {
            model.isLoading ->
                item {
                    UiPlaceholderLoader(
                        modifier = Modifier.padding(vertical = 12.dp).fillMaxWidth().height(40.dp)
                    )
                }

            model.isError ->
                item {
                    Text(
                        text = stringResource(R.string.reward_history_error),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.tertiary,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }

            else ->
                items(model.rows) { row ->
                    RewardRow(
                        coinIconRes = coinIconRes,
                        amount = amount(row.amount),
                        badge = row.date,
                        badgeColor = Theme.v2.colors.text.tertiary,
                    )
                }
        }
    }
}

@Composable
private fun RewardRow(
    @DrawableRes coinIconRes: Int,
    amount: String,
    badge: String,
    badgeColor: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier.clip(CircleShape)
                    .background(Theme.v2.colors.backgrounds.secondary)
                    .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = CircleShape)
                    .padding(12.dp)
        ) {
            Image(
                painter = painterResource(coinIconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }

        UiSpacer(12.dp)

        Text(
            text = amount,
            style = Theme.brockmann.body.m.medium,
            color = Theme.v2.colors.text.primary,
            modifier = Modifier.weight(1f),
        )

        UiSpacer(8.dp)

        Text(
            text = badge,
            style = Theme.satoshi.price.footnote,
            color = badgeColor,
            modifier =
                Modifier.border(
                        width = 1.dp,
                        color = Theme.v2.colors.border.light,
                        shape = Theme.v2.radius.sm,
                    )
                    .padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

private val HIDE_BALANCE_CHARS = "• ".repeat(8).trim()

@Preview
@Composable
private fun BondRewardHistoryContentPreview() {
    BondRewardHistoryContent(
        model =
            BondRewardHistoryUiModel(
                nodeAddress = "thor10czf2s89h79fsjmqqck85cdqeq536hw5ngz4lt",
                upcoming = "18.2031 RUNE",
                isLoading = false,
                totalEarned = "62.0000 RUNE",
                rows =
                    listOf(
                        BondRewardRowUiModel(amount = "20.0000 RUNE", date = "Sep 21, 2026"),
                        BondRewardRowUiModel(amount = "20.0000 RUNE", date = "Sep 14, 2026"),
                        BondRewardRowUiModel(amount = "22.0000 RUNE", date = "Sep 7, 2026"),
                    ),
            ),
        coinIconRes = R.drawable.rune,
        isBalanceVisible = true,
    )
}
