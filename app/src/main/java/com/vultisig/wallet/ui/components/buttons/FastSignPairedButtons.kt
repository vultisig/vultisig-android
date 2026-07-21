package com.vultisig.wallet.ui.components.buttons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R

/**
 * The dual sign CTA shown on the transaction verify screens when the vault supports fast signing.
 * - [onFastSignClick] triggers the Fast Vault server co-sign path (primary CTA).
 * - [onPairedSignClick] triggers the paired-device sign flow (secondary CTA with a devices icon).
 */
@Composable
fun FastSignPairedButtons(
    onFastSignClick: () -> Unit,
    onPairedSignClick: () -> Unit,
    modifier: Modifier = Modifier,
    state: VsButtonState = VsButtonState.Enabled,
    isLoading: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        // Figma order: Paired is the narrow leading button (wraps its content), Fast Sign is the
        // wide trailing CTA that fills the remaining width.
        VsButton(
            label = stringResource(R.string.verify_transaction_paired_btn_title),
            iconLeft = R.drawable.ic_paired_devices,
            variant = VsButtonVariant.Secondary,
            state = state,
            isLoading = isLoading,
            onClick = onPairedSignClick,
        )

        VsButton(
            label = stringResource(R.string.verify_transaction_fast_sign_btn_title),
            variant = VsButtonVariant.CTA,
            state = state,
            isLoading = isLoading,
            onClick = onFastSignClick,
            modifier = Modifier.weight(1f),
        )
    }
}

@Preview
@Composable
private fun FastSignPairedButtonsPreview() {
    FastSignPairedButtons(onFastSignClick = {}, onPairedSignClick = {})
}
