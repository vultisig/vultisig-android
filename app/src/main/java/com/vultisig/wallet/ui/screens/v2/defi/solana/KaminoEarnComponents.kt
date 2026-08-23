package com.vultisig.wallet.ui.screens.v2.defi.solana

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.vultisig.wallet.data.blockchain.solana.kamino.KaminoCurator
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

/** Both marks are 36dp and overlap by 12dp, so the pair measures 60dp rather than 72dp. */
private val LOGO_SIZE = 36.dp
private val LOGO_PAIR_WIDTH = 60.dp

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
                            text =
                                stringResource(
                                    R.string.kamino_earn_curated_by,
                                    vault.curator.displayName,
                                ),
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

/**
 * The Kamino Earn segment: one card per enabled vault.
 *
 * No total of its own — the Solana header banner above already adds these to native staking, and
 * the design draws that as the only total on the screen.
 */
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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (state.loadFailed) {
                    Text(
                        text = stringResource(R.string.kamino_earn_load_failed),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.alerts.warning,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
                    )
                }

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
private fun KaminoVaultCard(
    row: KaminoEarnRow,
    isLoading: Boolean,
    isBalanceVisible: Boolean,
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
) {
    Column(
        modifier = Modifier.kaminoCard().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        VaultIdentityRow(row)

        // A vault read as holding nothing describes no position, and rows of zeros describe nothing
        // — so the figures go and Deposit takes the full width, as the design draws an empty card.
        // An *unread* position keeps them: not knowing is not the same as knowing there is nothing.
        if (row.hasPosition) {
            PositionFigureRow(
                // A deposit of zero is a real value, so the amount is never placeheld — unlike its
                // price, which is genuinely absent until the quote lands.
                label =
                    stringResource(
                        R.string.kamino_earn_deposited,
                        row.depositedDisplay.orHidden(isBalanceVisible),
                    ),
                fiat = row.depositedFiat,
                isLoading = isLoading,
                isBalanceVisible = isBalanceVisible,
            )

            PositionFigureRow(
                label =
                    stringResource(
                        // Below zero this reads "Lost", not "Earned": the source is totalPnl, which
                        // goes negative, and the figure beside it is unsigned, so the label is the
                        // only thing saying which way the position moved.
                        if (row.pnlDirection == KaminoEarnRow.PnlDirection.DOWN) {
                            R.string.kamino_earn_lost
                        } else {
                            R.string.kamino_earn_earned
                        },
                        (row.pnlDisplay ?: FIAT_VALUE_UNAVAILABLE).orHidden(isBalanceVisible),
                    ),
                fiat = row.pnlFiat,
                isLoading = isLoading,
                isBalanceVisible = isBalanceVisible,
            )
        }

        // APY is a property of the vault rather than of the position, so it stays either way.
        ApyRow(apyDisplay = row.apyDisplay, isLoading = isLoading)

        // The rule the design draws is one divider per card, immediately above two buttons. An
        // empty card has a single full-width Deposit and no divider at all.
        if (row.hasPosition) {
            UiHorizontalDivider()
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
        VaultLogoPair(row)

        UiSpacer(8.dp)

        Column(modifier = Modifier.weight(1f)) {
            // The risk tier shares the name's line rather than the subtitle's: it is two short
            // fixed strings against a live vault name that can run to any length, so the name is
            // the one that yields.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name,
                    style = Theme.brockmann.body.m.medium,
                    color = Theme.v2.colors.text.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                UiSpacer(8.dp)

                Text(
                    text = stringResource(row.riskTier.labelRes),
                    style = Theme.brockmann.supplementary.caption,
                    color = row.riskTier.labelColor(),
                    maxLines = 1,
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                TokenLogo(
                    logo = R.drawable.kamino,
                    title = "",
                    modifier = Modifier.size(16.dp),
                    errorLogoModifier = Modifier.size(16.dp).clip(Theme.v2.radius.pill),
                )

                UiSpacer(3.dp)

                Text(
                    text = stringResource(R.string.kamino_earn_protocol),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * The curator's mark with the vault's underlying token overlapping it.
 *
 * The token is drawn second so it sits on top, which is the order the design stacks them in.
 */
@Composable
private fun VaultLogoPair(row: KaminoEarnRow) {
    Box(modifier = Modifier.size(width = LOGO_PAIR_WIDTH, height = LOGO_SIZE)) {
        TokenLogo(
            logo = row.curator.logoRes,
            title = row.curator.displayName,
            modifier = Modifier.size(LOGO_SIZE).align(Alignment.CenterStart),
            errorLogoModifier = Modifier.size(LOGO_SIZE).clip(Theme.v2.radius.pill),
        )

        TokenLogo(
            // The row carries the logo *name*; Coil needs it resolved to a drawable or it renders
            // nothing at all.
            logo = getCoinLogo(row.tokenLogo),
            title = row.tokenTicker,
            modifier = Modifier.size(LOGO_SIZE).align(Alignment.CenterEnd),
            errorLogoModifier = Modifier.size(LOGO_SIZE).clip(Theme.v2.radius.pill),
        )
    }
}

/**
 * One position figure: the amount inside the label, its price opposite.
 *
 * [isLoading] only reaches the price — the amount is either known or stands as
 * [FIAT_VALUE_UNAVAILABLE], and a shimmer where a figure already sits would read as the position
 * itself being re-read.
 */
@Composable
private fun PositionFigureRow(
    label: String,
    fiat: String?,
    isLoading: Boolean,
    isBalanceVisible: Boolean,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = Theme.brockmann.headings.title3,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )

        UiSpacer(8.dp)

        when {
            !isBalanceVisible ->
                Text(
                    text = HIDE_BALANCE_CHARS,
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.End,
                )
            fiat != null ->
                Text(
                    text = fiat,
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.tertiary,
                    textAlign = TextAlign.End,
                )
            // Sized to the value it stands in for, so the row does not resize when the price lands.
            isLoading ->
                UiPlaceholderLoader(modifier = Modifier.size(width = 72.dp, height = 14.dp))
            else ->
                Text(
                    text = FIAT_VALUE_UNAVAILABLE,
                    style = Theme.brockmann.headings.title3,
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
            label = stringResource(R.string.kamino_earn_apy),
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
private fun VaultActions(hasPosition: Boolean, onDeposit: () -> Unit, onWithdraw: () -> Unit) {
    // Withdraw only appears once there is something to withdraw; an untouched vault offers the one
    // action that makes sense, across the full width.
    // Both buttons are the design system's DeFi Button, taken from its two Figma variants rather
    // than styled here: Withdraw is `backgrounds/surface-2` #11284A with a 1dp
    // `borders/extra-light`
    // edge, Deposit is `buttons/cta-(primary)` #0B4EFF with a 1dp `primary/accent-3` edge, and both
    // carry a filled glyph in a white-12% circle. The previous values were why Withdraw had no
    // visible pill: `backgrounds.tertiary` (#0B1A3A) on a card painted `backgrounds.secondary`
    // (#061B3A) is a difference the eye cannot find, and the icon's circle was the card colour
    // exactly.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        if (hasPosition) {
            ActionButton(
                title = stringResource(R.string.withdraw),
                icon = R.drawable.circle_minus_filled,
                background = Theme.v2.colors.backgrounds.tertiary_2,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.03f)),
                contentColor = Theme.v2.colors.text.primary,
                iconCircleColor = Color.White.copy(alpha = 0.12f),
                iconCircleSize = 34.dp,
                iconSize = 16.dp,
                modifier = Modifier.weight(1f),
                onClick = onWithdraw,
            )
        }

        ActionButton(
            title = stringResource(R.string.kamino_earn_deposit),
            icon = R.drawable.circle_plus_filled,
            background = Theme.v2.colors.buttons.ctaPrimary,
            border = BorderStroke(1.dp, Theme.v2.colors.primary.accent3),
            contentColor = Theme.v2.colors.text.primary,
            iconCircleColor = Color.White.copy(alpha = 0.12f),
            iconCircleSize = 34.dp,
            iconSize = 16.dp,
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

private fun String.orHidden(isBalanceVisible: Boolean): String =
    if (isBalanceVisible) this else HIDE_BALANCE_CHARS

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

/** Exhaustive by construction: a new curator does not compile until it is given a mark. */
private val KaminoCurator.logoRes: Int
    @DrawableRes
    get() =
        when (this) {
            KaminoCurator.STEAKHOUSE_FINANCIAL -> R.drawable.curator_steakhouse
            KaminoCurator.ROCKAWAYX -> R.drawable.curator_rockawayx
            KaminoCurator.ALLEZ_LABS -> R.drawable.curator_allez
        }

@Preview(showBackground = true)
@Composable
private fun KaminoEarnTabContentPreview() {
    KaminoEarnTabContent(
        state =
            KaminoEarnUiModel(
                hasEnabledVaults = true,
                rows =
                    listOf(
                        KaminoEarnRow(
                            vaultAddress = "HDsayqAsDWy3QvANGqh2yNraqcD8Fnjgh73Mhb3WRS5E",
                            name = "Steakhouse USDC",
                            curator = KaminoCurator.STEAKHOUSE_FINANCIAL,
                            riskTier = KaminoRiskTier.CONSERVATIVE,
                            tokenLogo = "usdc",
                            tokenTicker = "USDC",
                            depositedDisplay = "1000 USDC",
                            depositedFiat = "$1,000.23",
                            apyDisplay = "4.00%",
                            pnlDisplay = "200 USDC",
                            pnlFiat = "$200.54",
                            pnlDirection = KaminoEarnRow.PnlDirection.UP,
                            fiatValue = BigDecimal("1000.23"),
                            hasPosition = true,
                        ),
                        KaminoEarnRow(
                            vaultAddress = "DWSXb18xZApz29vnQpgR2m6MynCT7PznaXt7Ut7M7KaP",
                            name = "RWA USDC",
                            curator = KaminoCurator.ROCKAWAYX,
                            riskTier = KaminoRiskTier.PRIVATE_CREDIT,
                            tokenLogo = "usdc",
                            tokenTicker = "USDC",
                            depositedDisplay = "0 USDC",
                            depositedFiat = "$0.00",
                            apyDisplay = "5.88%",
                            pnlDisplay = "0 USDC",
                            pnlFiat = "$0.00",
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
