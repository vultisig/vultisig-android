package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoRiskTier
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoVaultRegistry
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.ui.components.TokenLogo
import com.vultisig.wallet.ui.components.UiHorizontalDivider
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.screens.v2.defi.ActionButton
import com.vultisig.wallet.ui.screens.v2.defi.FIAT_VALUE_UNAVAILABLE
import com.vultisig.wallet.ui.screens.v2.defi.InfoItem
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigDecimal

private val HIDE_BALANCE_CHARS = "• ".repeat(6).trim()

/**
 * Picker for which curated vaults the Earn tab shows.
 *
 * Without this the tab has no way to be populated at all — the opt-in gates every card, so the
 * feature is unreachable until the user can turn a vault on.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun KaminoVaultPickerSheet(
    selected: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onDone: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Theme.v2.colors.backgrounds.primary,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.kamino_earn_title),
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
            )

            KaminoVaultRegistry.ALLOW_LIST.forEach { vault ->
                val isSelected = vault.address in selected
                Row(
                    modifier =
                        Modifier.fillMaxWidth()
                            .kaminoCard()
                            .toggleable(
                                value = isSelected,
                                role = Role.Checkbox,
                                onValueChange = { onToggle(vault.address, it) },
                            )
                            .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = vault.fallbackName,
                            style = Theme.brockmann.body.m.medium,
                            color = Theme.v2.colors.text.primary,
                        )
                        Text(
                            text = stringResource(R.string.kamino_earn_curated_by, vault.curator),
                            style = Theme.brockmann.body.s.medium,
                            color = Theme.v2.colors.text.tertiary,
                        )
                    }

                    Text(
                        text = stringResource(vault.riskTier.labelRes),
                        style = Theme.brockmann.supplementary.caption,
                        color = vault.riskTier.labelColor(),
                    )

                    UiSpacer(12.dp)

                    Checkbox(checked = isSelected, onCheckedChange = null)
                }
            }

            VsButton(
                label = stringResource(R.string.save_changes),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** The Kamino Earn segment: a total across enabled vaults, then one card each. */
@Composable
internal fun KaminoEarnTabContent(
    state: KaminoEarnUiModel,
    onDeposit: (String) -> Unit,
    onWithdraw: (String) -> Unit,
    emptyState: @Composable () -> Unit,
) {
    when {
        state.rows.isNotEmpty() || state.hasEnabledVaults ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                KaminoEarnTotalCard(
                    totalFiat = state.totalFiat,
                    isLoading = state.isLoading && state.totalFiat == null,
                    isBalanceVisible = state.isBalanceVisible,
                )

                state.rows.forEach { row ->
                    KaminoVaultCard(
                        row = row,
                        isLoading = state.isLoading,
                        isBalanceVisible = state.isBalanceVisible,
                        onDeposit = { onDeposit(row.vaultAddress) },
                        onWithdraw = { onWithdraw(row.vaultAddress) },
                    )
                }
            }

        else -> emptyState()
    }
}

@Composable
private fun KaminoEarnTotalCard(totalFiat: String?, isLoading: Boolean, isBalanceVisible: Boolean) {
    Column(modifier = Modifier.kaminoCard().padding(16.dp)) {
        Text(
            text = stringResource(R.string.kamino_earn_title),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(2.dp)

        when {
            isLoading ->
                UiPlaceholderLoader(modifier = Modifier.size(width = 120.dp, height = 28.dp))
            else ->
                Text(
                    text =
                        when {
                            !isBalanceVisible -> HIDE_BALANCE_CHARS
                            else -> totalFiat ?: FIAT_VALUE_UNAVAILABLE
                        },
                    style = Theme.brockmann.headings.title1,
                    color = Theme.v2.colors.text.primary,
                )
        }
    }
}

@Composable
private fun KaminoVaultCard(
    row: KaminoEarnRow,
    isLoading: Boolean,
    isBalanceVisible: Boolean,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Column(
        modifier = Modifier.kaminoCard().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        VaultIdentityRow(row)

        UiHorizontalDivider()

        // A vault read as holding nothing describes no position, and rows of zeros describe nothing
        // — so the figures go and Deposit takes the full width, as the design draws an empty card.
        // An *unread* position keeps them: not knowing is not the same as knowing there is nothing.
        if (row.hasPosition) {
            DepositedRow(row = row, isBalanceVisible = isBalanceVisible)
        }

        // APY is a property of the vault rather than of the position, so it stays either way.
        ApyRow(apyDisplay = row.apyDisplay, isLoading = isLoading)

        if (row.hasPosition) {
            PnlRow(row = row, isLoading = isLoading, isBalanceVisible = isBalanceVisible)
        }

        VaultActions(
            // Gated on the position itself, not its fiat value: a failed price lookup leaves
            // fiatValue at zero, and hiding Withdraw then would strand a real balance.
            hasPosition = row.hasPosition,
            onDeposit = onDeposit,
            onWithdraw = onWithdraw,
        )
    }
}

@Composable
private fun VaultIdentityRow(row: KaminoEarnRow) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TokenLogo(
            // The row carries the logo *name*; Coil needs it resolved to a drawable or it renders
            // nothing at all.
            logo = getCoinLogo(row.tokenLogo),
            title = row.tokenTicker,
            modifier = Modifier.size(36.dp),
            errorLogoModifier = Modifier.size(36.dp).clip(Theme.v2.radius.pill),
        )

        UiSpacer(12.dp)

        Column(modifier = Modifier.weight(1f)) {
            // The risk tier shares the name's line rather than the curator's. "Curated by
            // Steakhouse Financial" needs nearly the full width, so pairing the tier with it
            // truncated the curator while the name line beside it sat half empty. The tier is two
            // short fixed strings, so the name is the one that yields.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                UiSpacer(8.dp)

                Text(
                    text = stringResource(row.riskTier.labelRes),
                    style = Theme.brockmann.supplementary.caption,
                    color = row.riskTier.labelColor(),
                    maxLines = 1,
                )
            }

            UiSpacer(2.dp)

            Text(
                text = stringResource(R.string.kamino_earn_curated_by, row.curator),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DepositedRow(row: KaminoEarnRow, isBalanceVisible: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = stringResource(R.string.kamino_earn_deposited),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(1f)

        // A deposit of zero is a real value, so this is never placeheld — unlike APY and PnL, which
        // are genuinely absent until their calls answer.
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = if (isBalanceVisible) row.depositedDisplay else HIDE_BALANCE_CHARS,
                style = Theme.brockmann.body.m.medium,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.End,
            )

            Text(
                text =
                    when {
                        !isBalanceVisible -> HIDE_BALANCE_CHARS
                        else -> row.depositedFiat ?: FIAT_VALUE_UNAVAILABLE
                    },
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.End,
            )
        }
    }
}

@Composable
private fun ApyRow(apyDisplay: String?, isLoading: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        InfoItem(
            icon = R.drawable.ic_icon_percentage,
            label = stringResource(R.string.kamino_earn_apy_30d),
            value = null,
        )

        UiSpacer(1f)

        when {
            apyDisplay != null ->
                Text(
                    text = apyDisplay,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.alerts.success,
                )
            // Sized to the value it stands in for, so the row does not resize when the number
            // lands.
            isLoading ->
                UiPlaceholderLoader(modifier = Modifier.size(width = 54.dp, height = 14.dp))
            else ->
                Text(
                    text = FIAT_VALUE_UNAVAILABLE,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
        }
    }
}

@Composable
private fun PnlRow(row: KaminoEarnRow, isLoading: Boolean, isBalanceVisible: Boolean) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            // Below zero this reads "Lost", not "Earned": the source is totalPnl, which goes
            // negative, and "Earned: -3 USDC" asserts the loss was earned and leaves the colour to
            // correct it.
            text =
                stringResource(
                    if (row.pnlDirection == KaminoEarnRow.PnlDirection.DOWN) {
                        R.string.kamino_earn_lost
                    } else {
                        R.string.kamino_earn_pnl
                    }
                ),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
        )

        UiSpacer(1f)

        when {
            row.pnlDisplay != null ->
                Text(
                    text = if (isBalanceVisible) row.pnlDisplay else HIDE_BALANCE_CHARS,
                    style = Theme.brockmann.supplementary.caption,
                    color = row.pnlDirection.color(),
                )
            isLoading ->
                UiPlaceholderLoader(modifier = Modifier.size(width = 72.dp, height = 14.dp))
            else ->
                Text(
                    text = FIAT_VALUE_UNAVAILABLE,
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.tertiary,
                )
        }
    }
}

@Composable
private fun VaultActions(hasPosition: Boolean, onDeposit: () -> Unit, onWithdraw: () -> Unit) {
    // Withdraw only appears once there is something to withdraw; an untouched vault offers the one
    // action that makes sense, across the full width.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (hasPosition) {
            ActionButton(
                title = stringResource(R.string.withdraw),
                icon = R.drawable.circle_minus,
                background = Theme.v2.colors.backgrounds.tertiary,
                contentColor = Theme.v2.colors.text.primary,
                iconCircleColor = Theme.v2.colors.backgrounds.secondary,
                modifier = Modifier.weight(1f),
                onClick = onWithdraw,
            )
        }

        ActionButton(
            title = stringResource(R.string.kamino_earn_deposit),
            icon = R.drawable.circle_plus,
            background = Theme.v2.colors.primary.accent4,
            contentColor = Theme.v2.colors.text.primary,
            iconCircleColor = Theme.v2.colors.primary.accent3,
            modifier = Modifier.weight(1f),
            onClick = onDeposit,
        )
    }
}

@Composable
private fun Modifier.kaminoCard(): Modifier =
    fillMaxWidth()
        .clip(Theme.v2.radius.xl)
        .background(Theme.v2.colors.backgrounds.secondary)
        .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = Theme.v2.radius.xl)

private val KaminoRiskTier.labelRes: Int
    get() =
        when (this) {
            KaminoRiskTier.CONSERVATIVE -> R.string.kamino_earn_risk_conservative
            KaminoRiskTier.PRIVATE_CREDIT -> R.string.kamino_earn_risk_private_credit
        }

@Composable
private fun KaminoRiskTier.labelColor(): Color =
    when (this) {
        KaminoRiskTier.CONSERVATIVE -> Theme.v2.colors.text.tertiary
        // Lending against tokenized private credit is a materially different risk and must not read
        // as the same product as the plain lending vaults.
        KaminoRiskTier.PRIVATE_CREDIT -> Theme.v2.colors.alerts.warning
    }

@Composable
private fun KaminoEarnRow.PnlDirection.color(): Color =
    when (this) {
        KaminoEarnRow.PnlDirection.UP -> Theme.v2.colors.alerts.success
        KaminoEarnRow.PnlDirection.DOWN -> Theme.v2.colors.alerts.error
        // Every vault the user never deposited into sits at zero; green would read as a gain.
        KaminoEarnRow.PnlDirection.FLAT -> Theme.v2.colors.text.primary
    }

@Preview(showBackground = true)
@Composable
private fun KaminoEarnTabContentPreview() {
    KaminoEarnTabContent(
        state =
            KaminoEarnUiModel(
                hasEnabledVaults = true,
                totalFiat = "$3,010.77",
                rows =
                    listOf(
                        KaminoEarnRow(
                            vaultAddress = "HDsayqAsDWy3QvANGqh2yNraqcD8Fnjgh73Mhb3WRS5E",
                            name = "Steakhouse USDC",
                            curator = "Steakhouse Financial",
                            riskTier = KaminoRiskTier.CONSERVATIVE,
                            tokenLogo = "usdc",
                            tokenTicker = "USDC",
                            depositedDisplay = "1000 USDC",
                            depositedFiat = "$1,000.23",
                            apyDisplay = "4.00%",
                            pnlDisplay = "200 USDC",
                            pnlDirection = KaminoEarnRow.PnlDirection.UP,
                            fiatValue = BigDecimal("1000.23"),
                        ),
                        KaminoEarnRow(
                            vaultAddress = "DWSXb18xZApz29vnQpgR2m6MynCT7PznaXt7Ut7M7KaP",
                            name = "RWA USDC",
                            curator = "RockawayX",
                            riskTier = KaminoRiskTier.PRIVATE_CREDIT,
                            tokenLogo = "usdc",
                            tokenTicker = "USDC",
                            depositedDisplay = "0 USDC",
                            depositedFiat = "$0.00",
                            apyDisplay = "5.88%",
                            pnlDisplay = "0 USDC",
                            pnlDirection = KaminoEarnRow.PnlDirection.FLAT,
                            fiatValue = BigDecimal.ZERO,
                        ),
                    ),
            ),
        onDeposit = {},
        onWithdraw = {},
        emptyState = {},
    )
}
