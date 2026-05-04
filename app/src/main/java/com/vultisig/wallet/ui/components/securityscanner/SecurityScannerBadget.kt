package com.vultisig.wallet.ui.components.securityscanner

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.theme.Theme
import timber.log.Timber

/**
 * Renders the dApp signing security badge above the verify card.
 *
 * Three states map to the Figma transaction-overview hero header:
 * - [TransactionScanStatus.Scanning] — small loading spinner + "scanning..."
 * - [TransactionScanStatus.Scanned] — green checkmark + "Transaction scanned by" + Blockaid logo
 * - [TransactionScanStatus.Error] — secondary triangle + "Transaction not scanned by" + logo
 *
 * Layout values come from Figma node 51409:89299 (scanning), 41708:71768 (not scanned) and
 * 37372:55934 (scanned). Icon and logo sizes are deliberately smaller than the surrounding body
 * copy so the badge reads as supplementary metadata rather than a CTA.
 */
@Composable
internal fun SecurityScannerBadget(status: TransactionScanStatus) {
    // [NotStarted] is the initial state; the badge stays absent until the scanner kicks off,
    // which happens automatically on every verify-screen entry. Returning early avoids reserving
    // a 20dp slot for an empty Row.
    when (status) {
        is TransactionScanStatus.Scanned ->
            BadgeRow { ScannedBadget(providerLogoId = status.result.provider) }
        is TransactionScanStatus.Scanning -> BadgeRow { ScanningBadget() }
        is TransactionScanStatus.Error ->
            BadgeRow { NotScannedBadget(providerLogoId = status.provider) }
        is TransactionScanStatus.NotStarted -> Unit
    }
}

@Composable
private fun BadgeRow(content: @Composable RowScope.() -> Unit) {
    // [heightIn] (vs a fixed [height]) lets the row grow when font scale or a longer translation
    // pushes the footnote line height past the design baseline; the 20dp design value remains the
    // minimum so the inline brand mark and check icon still align with surrounding metadata at
    // default density.
    Row(
        modifier = Modifier.heightIn(min = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun ScanningBadget() {
    // Figma uses a 16px loader-circle glyph; CircularProgressIndicator with a narrower stroke
    // matches that visual weight without bundling another icon.
    CircularProgressIndicator(
        color = Theme.v2.colors.text.secondary,
        modifier = Modifier.size(16.dp),
        strokeWidth = 1.5.dp,
    )

    Spacer(modifier = Modifier.width(2.dp))

    Text(
        text = stringResource(R.string.security_scanner_transaction_scanning),
        style = Theme.brockmann.supplementary.footnote,
        color = Theme.v2.colors.text.secondary,
    )
}

@Composable
private fun ScannedBadget(providerLogoId: String) {
    Icon(
        painter = painterResource(id = R.drawable.ic_check),
        contentDescription = null,
        tint = Theme.v2.colors.alerts.success,
        modifier = Modifier.size(20.dp),
    )

    Spacer(modifier = Modifier.width(2.dp))

    Text(
        text = stringResource(R.string.security_scanner_transaction_scanned_by),
        style = Theme.brockmann.supplementary.footnote,
        color = Theme.v2.colors.text.secondary,
    )

    Spacer(modifier = Modifier.width(4.dp))

    Image(
        painter = painterResource(id = getSecurityScannerLogo(providerLogoId)),
        contentDescription = null,
        // 10dp height matches the inline brand mark in Figma — bigger than 16dp
        // would compete with the body copy and read like a button.
        modifier = Modifier.height(10.dp),
    )
}

@Composable
private fun NotScannedBadget(providerLogoId: String) {
    Icon(
        painter = painterResource(id = R.drawable.ic_triangle_alert),
        contentDescription = null,
        tint = Theme.v2.colors.text.secondary,
        modifier = Modifier.size(16.dp),
    )

    Spacer(modifier = Modifier.width(2.dp))

    Text(
        text = stringResource(R.string.security_scanner_transaction_not_scanned),
        style = Theme.brockmann.supplementary.footnote,
        color = Theme.v2.colors.text.secondary,
    )

    Spacer(modifier = Modifier.width(4.dp))

    Image(
        painter = painterResource(id = getSecurityScannerLogo(providerLogoId)),
        contentDescription = null,
        modifier = Modifier.height(10.dp),
    )
}

internal fun getSecurityScannerLogo(provider: String): Int {
    return when (provider) {
        BLOCKAID_PROVIDER -> R.drawable.blockaid_logo
        else -> {
            Timber.w("SecurityScanner: Unknown provider logo requested: %s", provider)
            R.drawable.blockaid_logo
        }
    }
}
