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
import com.vultisig.wallet.data.chains.helpers.RippleDappTransactionDecoder
import com.vultisig.wallet.data.chains.helpers.RippleDappTx
import com.vultisig.wallet.ui.theme.Theme

/**
 * Verify-screen card for a dApp-supplied XRPL transaction (SignRipple). The raw JSON is signed
 * verbatim, so there is no native `to_address` / `to_amount` to render as a normal send. Instead we
 * show the decoded terms — type, source, destination, amounts, issuer — and always keep the raw
 * JSON available in an expandable section so a co-signer is never shown a blank/misleading screen
 * even when [RippleDappTx.fields] is empty (the JSON could not be decoded).
 */
@Composable
fun SignRippleDisplayView(
    tx: RippleDappTx,
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
                text = stringResource(R.string.ripple_transaction_summary),
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
                tx.fields.forEach { field ->
                    VerifyCardJsonDetails(
                        title = field.label,
                        subtitle = field.value,
                        modifier = rowModifier,
                    )
                }
                // Always surface the raw JSON — it is the source of truth for what gets signed, and
                // the sole content when decoding produced no fields.
                VerifyCardJsonDetails(
                    title = stringResource(R.string.raw_transaction_data),
                    subtitle = tx.rawJson,
                    modifier = rowModifier,
                )
            }
        }
    }
}

private val rowModifier: Modifier
    @Composable
    get() =
        Modifier.fillMaxWidth()
            .background(
                color = Theme.v2.colors.variables.bordersLight,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp)

private const val PREVIEW_RIPPLE_JSON =
    "{\"TransactionType\":\"Payment\",\"Account\":\"rB5TihdPbKgMrkFqrqUC3yLdE8hhv4BdeY\"," +
        "\"Destination\":\"rNXEkKCxvfLcM1h4HJkaj2FtmYuAWrsGbY\",\"Amount\":\"1500000\"," +
        "\"SendMax\":{\"currency\":\"USD\",\"issuer\":\"rMwjYedjc7qqtKYVLiAccJSmCwih4LnE2q\"," +
        "\"value\":\"1.5\"},\"DestinationTag\":\"12345\"}"

@Preview
@Composable
private fun PreviewSignRippleDisplayView() {
    SignRippleDisplayView(
        tx = RippleDappTransactionDecoder.decode(PREVIEW_RIPPLE_JSON),
        initiallyExpanded = true,
    )
}
