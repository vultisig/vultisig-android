package com.vultisig.wallet.ui.screens.v2.defi

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.ui.components.PercentageChip
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonState
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.models.defi.BondedNodeUiModel
import com.vultisig.wallet.ui.models.defi.RewardsPayoutUiState
import com.vultisig.wallet.ui.models.defi.RewardsPayoutViewModel
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.roundToInt

private val payoutShortcuts = listOf(25, 50, 75, 100)
private const val RUNE_TICKER = "RUNE"
private val HIDE_BALANCE_CHARS = "• ".repeat(8).trim()

/**
 * Rewards Payout for one bonded THORChain node: the share of its future rewards paid out to the
 * vault in RUNE (THORChain Bond Yield). Continue builds the deposit and opens the verify screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RewardsPayoutSheet(
    vaultId: String,
    node: BondedNodeUiModel,
    isBalanceVisible: Boolean,
    onDismissRequest: () -> Unit,
    model: RewardsPayoutViewModel = hiltViewModel(key = node.fullAddress),
) {
    val state by model.state.collectAsState()

    LaunchedEffect(vaultId, node.fullAddress) { model.load(vaultId, node.fullAddress) }
    DisposableEffect(model) { onDispose { model.cancel() } }

    V2BottomSheet(onDismissRequest = onDismissRequest) {
        RewardsPayoutContent(
            state = state,
            node = node,
            isBalanceVisible = isBalanceVisible,
            onPercentageChange = model::setPercentage,
            onRetry = model::retry,
            onContinue = { model.submit(onSubmitted = onDismissRequest) },
        )
    }
}

@Composable
private fun RewardsPayoutContent(
    state: RewardsPayoutUiState,
    node: BondedNodeUiModel,
    isBalanceVisible: Boolean,
    onPercentageChange: (Int) -> Unit,
    onRetry: () -> Unit,
    onContinue: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 31.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.rewards_payout),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
            )
            Text(
                text = stringResource(R.string.rewards_payout_description),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PayoutAsset()
            PayoutPercentage(state, onPercentageChange, onRetry)
        }

        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            InfoRow(stringResource(R.string.node_address), node.address)
            InfoRow(
                stringResource(R.string.defi_tab_bonded),
                if (isBalanceVisible) node.bondedAmount else HIDE_BALANCE_CHARS,
            )
            InfoRow(
                stringResource(R.string.next_reward),
                if (isBalanceVisible) node.nextAward else HIDE_BALANCE_CHARS,
            )
        }

        if (state.submitFailed) {
            Text(
                text = stringResource(R.string.dialog_default_error_body),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.alerts.error,
            )
        }

        VsButton(
            label = stringResource(R.string.send_continue_button),
            state = if (state.canContinue) VsButtonState.Enabled else VsButtonState.Disabled,
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PayoutAsset() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.payout_asset),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
        Row(
            modifier = Modifier.fillMaxWidth().card().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.rune),
                contentDescription = null,
                modifier = Modifier.size(36.dp),
            )
            UiSpacer(12.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = RUNE_TICKER,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                )
                Text(
                    text = Chain.ThorChain.raw,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
            }
        }
    }
}

@Composable
private fun PayoutPercentage(
    state: RewardsPayoutUiState,
    onPercentageChange: (Int) -> Unit,
    onRetry: () -> Unit,
) {
    val isLoaded = state.currentBps != null
    Column(
        modifier = Modifier.fillMaxWidth().card().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.payout_percentage),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.secondary,
                modifier = Modifier.weight(1f),
            )
            when {
                state.loadFailed ->
                    Text(
                        text = stringResource(R.string.try_again),
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                        modifier = Modifier.clickable(onClick = onRetry),
                    )
                !isLoaded ->
                    UiPlaceholderLoader(modifier = Modifier.size(width = 64.dp, height = 34.dp))
                else ->
                    Text(
                        text =
                            stringResource(R.string.remove_pool_percent_format, state.percentage),
                        style = Theme.satoshi.price.title1,
                        color = Theme.v2.colors.text.primary,
                    )
            }
        }

        PercentSlider(
            percent = state.percentage / 100f,
            onPercentChanged = { onPercentageChange((it * 100).roundToInt()) },
            enabled = isLoaded,
            steps = 0,
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            payoutShortcuts.forEach { share ->
                PercentageChip(
                    title =
                        if (share == 100) stringResource(R.string.max)
                        else stringResource(R.string.remove_pool_percent_format, share),
                    isSelected = state.percentage == share,
                    onClick = { if (isLoaded) onPercentageChange(share) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (isLoaded) {
            Text(
                text =
                    if (state.percentage == 0) stringResource(R.string.rewards_payout_caption_none)
                    else stringResource(R.string.rewards_payout_caption, state.percentage),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Composable
private fun Modifier.card(): Modifier =
    background(color = Theme.v2.colors.backgrounds.secondary, shape = Theme.v2.radius.xl)
        .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = Theme.v2.radius.xl)
