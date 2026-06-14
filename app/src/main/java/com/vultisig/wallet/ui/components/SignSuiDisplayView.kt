package com.vultisig.wallet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

/**
 * Verify-screen card for a dApp-supplied Sui Programmable Transaction Block (SignSui). The PTB's
 * `TransactionData` is signed verbatim from its BCS bytes, so there is no `to_address` /
 * `to_amount` to render as a normal send. Instead we show the signer and the raw base64 bytes that
 * will be signed, mirroring [SignSolanaDisplayView]'s raw-transaction display.
 *
 * @param sender the vault address signing the PTB.
 * @param unsignedTxMsg base64-encoded `TransactionData` BCS bytes.
 */
@Composable
fun SignSuiDisplayView(
    sender: String,
    unsignedTxMsg: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var isExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Absolute.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.sui_raw_transaction),
                style = Theme.brockmann.button.medium.regular,
                color = Theme.v2.colors.text.tertiary,
            )

            IconButton(onClick = { isExpanded = !isExpanded }, modifier = Modifier.size(10.dp)) {
                UiIcon(
                    drawableResId = R.drawable.chevron,
                    tint = Theme.v2.colors.neutrals.n100,
                    size = 8.dp,
                    modifier = Modifier.graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
                )
            }
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (sender.isNotBlank()) {
                    VerifyCardJsonDetails(
                        title = stringResource(R.string.sui_sender),
                        subtitle = sender,
                        modifier =
                            Modifier.fillMaxWidth()
                                .background(
                                    color = Theme.v2.colors.variables.bordersLight,
                                    shape = RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 12.dp),
                    )
                }
                VerifyCardJsonDetails(
                    title = stringResource(R.string.raw_transaction_data),
                    subtitle = unsignedTxMsg,
                    modifier =
                        Modifier.fillMaxWidth()
                            .background(
                                color = Theme.v2.colors.variables.bordersLight,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .padding(horizontal = 12.dp),
                )
            }
        }
    }
}

private const val PREVIEW_SUI_PTB =
    "AAACAAgA4fUFAAAAAAAgWqQ5q8s0e0kq0a7s3w2QxJYwq7XmZ1pL0c1d8s2f3g4FAQEBAQABAAA" +
        "x2QxJYwq7XmZ1pL0c1d8s2f3g4Aq7XmZ1pL0c1d8s2f3g4Fy0kq0a7s3w2QxJYwq7XmZ1AQ=="

@Preview
@Composable
private fun PreviewSignSuiDisplayView() {
    SignSuiDisplayView(
        sender = "0x9a1b2c3d4e5f60718293a4b5c6d7e8f90123456789abcdef0123456789abcdef",
        unsignedTxMsg = PREVIEW_SUI_PTB,
        initiallyExpanded = true,
    )
}
