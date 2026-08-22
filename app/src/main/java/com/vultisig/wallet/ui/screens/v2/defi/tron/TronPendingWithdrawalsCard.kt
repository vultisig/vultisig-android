package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.tron.TronResourceType
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.models.defi.TronPendingWithdrawalUiModel
import com.vultisig.wallet.ui.models.defi.sunToTrx
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.formatTokenAmount
import kotlinx.coroutines.delay

private data class CountdownParts(val days: Long, val hours: Long, val minutes: Long)

/** Returns the days/hours/minutes remaining until [expiryEpochMs], or null if already expired. */
private fun countdownParts(expiryEpochMs: Long, nowMs: Long): CountdownParts? {
    if (expiryEpochMs <= nowMs) return null
    val remaining = expiryEpochMs - nowMs
    return CountdownParts(
        days = remaining / (1_000L * 60 * 60 * 24),
        hours = (remaining % (1_000L * 60 * 60 * 24)) / (1_000L * 60 * 60),
        minutes = (remaining % (1_000L * 60 * 60)) / (1_000L * 60),
    )
}

/**
 * Card listing the TRX still inside its unfreeze window, mirroring iOS' `pendingWithdrawalsCard`: a
 * header carrying the unfreezing total, one row per entry, and a single claim action.
 *
 * `WithdrawExpireUnfreeze` sweeps every matured entry in one transaction, so there is one button
 * for the whole card rather than a per-row action, which would suggest claims can be made
 * individually.
 */
@Composable
internal fun TronPendingWithdrawalsCard(
    withdrawals: List<TronPendingWithdrawalUiModel>,
    totalTrx: String,
    isBalanceVisible: Boolean,
    isClaiming: Boolean = false,
    onClaim: () -> Unit = {},
) {
    // One clock for the whole card, so a row flipping to "Ready to claim" and the button offering
    // to claim it cannot disagree.
    val nowMs by
        produceState(initialValue = System.currentTimeMillis(), key1 = withdrawals) {
            while (true) {
                val nextExpiryMs =
                    withdrawals.filter { it.expiryEpochMs > value }.minOfOrNull { it.expiryEpochMs }
                        ?: break
                val delta = nextExpiryMs - value
                delay(if (delta <= 60_000L) 1_000L else 60_000L)
                value = System.currentTimeMillis()
            }
        }

    val claimableSun = withdrawals.filter { it.expiryEpochMs <= nowMs }.sumOf { it.amountSun }

    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(Theme.v2.radius.xl)
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(1.dp, Theme.v2.colors.border.light, Theme.v2.radius.xl)
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(
                drawableResId = R.drawable.clock_arrow_circlepath,
                size = 20.dp,
                tint = Theme.v2.colors.text.secondary,
            )
            Text(
                text = stringResource(R.string.tron_defi_pending_withdrawals),
                style = Theme.brockmann.body.l.medium,
                color = Theme.v2.colors.text.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (isBalanceVisible) "$totalTrx TRX" else HIDE_BALANCE_CHARS,
                style = Theme.brockmann.body.l.medium,
                color = Theme.v2.colors.text.secondary,
            )
        }

        HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)

        withdrawals.forEach { withdrawal ->
            TronPendingWithdrawalRow(
                withdrawal = withdrawal,
                isBalanceVisible = isBalanceVisible,
                nowMs = nowMs,
            )
        }

        if (claimableSun > 0L) {
            val claimableText =
                if (isBalanceVisible) {
                    "${claimableSun.sunToTrx().stripTrailingZeros().formatTokenAmount()} TRX"
                } else {
                    HIDE_BALANCE_CHARS
                }
            TronDeFiActionButton(
                title = stringResource(R.string.tron_defi_claim_button, claimableText),
                icon = R.drawable.ic_arrow_down,
                background = Theme.v2.colors.buttons.ctaPrimary,
                border = BorderStroke(Dp.Hairline, Theme.v2.colors.primary.accent3),
                contentColor = Theme.v2.colors.text.primary,
                iconCircleColor = TronDeFiActionButtonIconCircleColor,
                enabled = !isClaiming,
                modifier = Modifier.fillMaxWidth(),
                onClick = onClaim,
            )
        }
    }
}

/** Row for a single pending withdrawal: amount, claim status, and the matching status icon. */
@Composable
private fun TronPendingWithdrawalRow(
    withdrawal: TronPendingWithdrawalUiModel,
    isBalanceVisible: Boolean,
    nowMs: Long,
) {
    val countdown = countdownParts(withdrawal.expiryEpochMs, nowMs)
    val isClaimable = countdown == null
    val statusText =
        when {
            countdown == null -> stringResource(R.string.tron_defi_ready_to_claim)
            countdown.days > 0 ->
                stringResource(R.string.tron_defi_countdown_days, countdown.days, countdown.hours)
            else ->
                stringResource(
                    R.string.tron_defi_countdown_hours,
                    countdown.hours,
                    countdown.minutes,
                )
        }

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = if (isBalanceVisible) "${withdrawal.amountTrx} TRX" else HIDE_BALANCE_CHARS,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                withdrawal.resourceType?.let { TronResourceTypeBadge(it) }
                Text(
                    text = statusText,
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.secondary,
                )
            }
        }

        UiIcon(
            drawableResId = if (isClaimable) R.drawable.check_3 else R.drawable.ic_hourglass,
            size = 20.dp,
            tint = Theme.v2.colors.text.secondary,
        )
    }
}

/** Pill badge showing the TRX resource type (bandwidth or energy) with icon and label. */
@Composable
private fun TronResourceTypeBadge(resourceType: TronResourceType) {
    val labelRes =
        when (resourceType) {
            TronResourceType.BANDWIDTH -> R.string.tron_resource_bandwidth
            TronResourceType.ENERGY -> R.string.tron_resource_energy
        }
    val iconRes =
        when (resourceType) {
            TronResourceType.BANDWIDTH -> R.drawable.bandwidth
            TronResourceType.ENERGY -> R.drawable.energy
        }
    val iconTint =
        when (resourceType) {
            TronResourceType.BANDWIDTH -> Theme.v2.colors.alerts.success
            TronResourceType.ENERGY -> Theme.v2.colors.alerts.warning
        }
    Row(
        modifier =
            Modifier.clip(RoundedCornerShape(6.dp))
                .background(Theme.v2.colors.backgrounds.surface2)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UiIcon(drawableResId = iconRes, size = 12.dp, tint = iconTint)
        Text(
            text = stringResource(labelRes),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.secondary,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TronPendingWithdrawalsCardPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        TronPendingWithdrawalsCard(
            withdrawals =
                listOf(
                    TronPendingWithdrawalUiModel(
                        amountTrx = "1",
                        expiryEpochMs = System.currentTimeMillis() - 1_000L,
                        resourceType = TronResourceType.BANDWIDTH,
                        amountSun = 1_000_000L,
                    ),
                    TronPendingWithdrawalUiModel(
                        amountTrx = "0.25",
                        expiryEpochMs = System.currentTimeMillis() + 2 * 24 * 60 * 60 * 1_000L,
                        resourceType = TronResourceType.ENERGY,
                        amountSun = 250_000L,
                    ),
                ),
            totalTrx = "1.25",
            isBalanceVisible = true,
        )
    }
}
