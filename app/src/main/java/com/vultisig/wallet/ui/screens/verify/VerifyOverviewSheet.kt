package com.vultisig.wallet.ui.screens.verify

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isLayer2
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.data.securityscanner.SecurityRiskLevel
import com.vultisig.wallet.ui.components.TokenAndChainLogo
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.securityscanner.SecurityScannerBottomSheetContent
import com.vultisig.wallet.ui.components.securityscanner.getSecurityScannerBottomSheetStyle
import com.vultisig.wallet.ui.components.v2.bottomsheets.OverviewBottomSheet
import com.vultisig.wallet.ui.components.v2.bottomsheets.OverviewSheetControlSize
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.theme.Theme

/**
 * The overview sheet the Send, Swap and Deposit reviews share: the floating card, the scan-status
 * control at the title's left, and the Blockaid verdict that takes the card over when a flagged
 * transaction is signed anyway.
 *
 * The verdict replaces [content] and [footer] together rather than opening a second sheet, so "Go
 * back" returns to the figures in place and "Continue anyway" carries on from where the sign tap
 * left off. A clean scan never interrupts: it only turns the control's ring green.
 */
@Composable
internal fun VerifyOverviewSheet(
    title: String,
    scanStatus: TransactionScanStatus,
    showScanningWarning: Boolean,
    onDismissRequest: () -> Unit,
    onContinueAnyway: () -> Unit,
    onDismissWarning: () -> Unit,
    footer: @Composable ColumnScope.() -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val warning = (scanStatus as? TransactionScanStatus.Scanned)?.takeIf { showScanningWarning }

    OverviewBottomSheet(
        title = title,
        onDismissRequest = onDismissRequest,
        leadingControl = { ScanStatusControl(status = scanStatus) },
        footer = footer.takeIf { warning == null },
    ) {
        // Back from the verdict returns to the figures, as "Go back" does; only the figures' own
        // back closes the sheet. Registered inside the sheet, whose window is the one that gets
        // the key.
        BackHandler(enabled = warning != null, onBack = onDismissWarning)

        if (warning != null) {
            SecurityScannerBottomSheetContent(
                contentStyle = warning.result.getSecurityScannerBottomSheetStyle(),
                // The provider is already named by the control in the header.
                securityScannerProvider = null,
                onDismissRequest = onDismissWarning,
                onContinueAnyway = onContinueAnyway,
            )
        } else {
            content()
        }
    }
}

/**
 * The scanner's mark, ringed in the colour of its verdict once there is one. Scanning, a scan that
 * failed and a scan not yet started all show the bare mark.
 */
@Composable
private fun ScanStatusControl(status: TransactionScanStatus) {
    val ring =
        when (status) {
            is TransactionScanStatus.Scanned ->
                when {
                    status.result.isSecure -> Theme.v2.colors.alerts.success
                    status.result.riskLevel == SecurityRiskLevel.HIGH ||
                        status.result.riskLevel == SecurityRiskLevel.CRITICAL ->
                        Theme.v2.colors.alerts.error
                    else -> Theme.v2.colors.alerts.warning
                }
            is TransactionScanStatus.Scanning,
            is TransactionScanStatus.Error,
            TransactionScanStatus.NotStarted -> Color.Transparent
        }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(OverviewSheetControlSize)
                .background(color = ScanControlBackground, shape = CircleShape)
                .border(width = 1.dp, color = ring, shape = CircleShape),
    ) {
        UiIcon(
            drawableResId = R.drawable.ic_blockaid_mark,
            size = 12.dp,
            tint = Theme.v2.colors.neutrals.n50,
        )
    }
}

/**
 * The amount the sheet leads with, at the weight of a heading: what is being sent or deposited,
 * with the asset's logo beside it and its fiat worth beneath. A [header] names the verb.
 */
@Composable
internal fun VerifyAmountHero(header: String, valuedToken: ValuedToken) {
    val token = valuedToken.token

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = header,
            style = Theme.brockmann.supplementary.captionSmall,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )

        UiSpacer(12.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TokenAndChainLogo(
                tokenLogo = getCoinLogo(token.logo),
                tokenTicker = token.ticker,
                chainLogo =
                    token.chain.monoToneLogo.takeIf {
                        !token.isNativeToken || token.chain.isLayer2
                    },
                tokenLogoSize = 24.dp,
                chainLogoSize = 14.dp,
                chainLogoOffset = DpOffset(x = 4.dp, y = 4.dp),
                chainBorderColor = Theme.v2.colors.backgrounds.surface1,
            )

            // An amount still loading leaves the ticker alone rather than a dangling space.
            Text(
                text =
                    listOf(valuedToken.value, token.ticker)
                        .filter { it.isNotEmpty() }
                        .joinToString(" "),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (valuedToken.fiatValue.isNotEmpty()) {
            UiSpacer(4.dp)

            Text(
                text = valuedToken.fiatValue,
                style = Theme.brockmann.supplementary.captionSmall,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/**
 * The source and destination of a transfer as two stacked cards joined by a chevron, the way the
 * overview sheet draws them in place of the From / To rows.
 *
 * Each card leads with a name when one is known (the vault, an address-book entry, a contract
 * label) and always carries the address, so a name never hides where the funds actually go.
 */
@Composable
internal fun VerifyAccountCards(
    fromName: String?,
    fromAddress: String,
    toName: String?,
    toAddress: String,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VerifyAccountCard(name = fromName, address = fromAddress)
            VerifyAccountCard(name = toName, address = toAddress)
        }

        VerifyPairNotch(chevron = R.drawable.ic_chevron_down_small)
    }
}

/**
 * The join between a pair of cards: a sheet-coloured disc over the gap, so the chevron sits in a
 * cutout shared by both cards instead of floating over one of them.
 */
@Composable
internal fun VerifyPairNotch(@DrawableRes chevron: Int) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier.size(40.dp)
                .background(color = Theme.v2.colors.backgrounds.surface1, shape = CircleShape)
                .border(width = 1.dp, color = Theme.v2.colors.border.light, shape = CircleShape),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.size(24.dp)
                    .background(color = Theme.v2.colors.backgrounds.tertiary_2, shape = CircleShape),
        ) {
            UiIcon(drawableResId = chevron, size = 12.dp, tint = PairChevronTint)
        }
    }
}

@Composable
private fun VerifyAccountCard(name: String?, address: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
        modifier =
            Modifier.fillMaxWidth()
                .defaultMinSize(minHeight = 82.dp)
                .background(
                    color = Theme.v2.colors.backgrounds.surface2,
                    shape = Theme.v2.radius.lg,
                )
                .padding(16.dp),
    ) {
        if (name != null) {
            Text(
                text = name,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Text(
            text = address,
            style = Theme.brockmann.body.s.medium,
            color =
                if (name != null) Theme.v2.colors.text.tertiary else Theme.v2.colors.text.secondary,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
    }
}

/** `Main Vault (0xF42…9Ac5)`, centred: the account the funds leave from, by name and address. */
@Composable
internal fun VerifyVaultRow(name: String, address: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = name,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )

        if (address.isNotEmpty()) {
            val display =
                if (address.length > 8) "(${address.take(4)}...${address.takeLast(4)})"
                else "($address)"
            Text(
                text = display,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                maxLines = 1,
            )
        }
    }
}

private val ScanControlBackground = Color(0xFF2C4163)
private val PairChevronTint = Color(0xFF718096)

/**
 * How a review is framed. A [Screen] draws its own card on a page of its own; a [Sheet] already is
 * the card, so the same details go straight onto it, without the chrome and the rules between rows
 * that the page needs.
 */
internal enum class VerifyPresentation {
    Screen,
    Sheet,
}
