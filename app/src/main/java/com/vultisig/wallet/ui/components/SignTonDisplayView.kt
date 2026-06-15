package com.vultisig.wallet.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.models.keysign.TonMessageOperation
import com.vultisig.wallet.ui.models.keysign.TonMessageUiModel
import com.vultisig.wallet.ui.theme.Theme

/**
 * Displays a collapsible list of decoded TonConnect messages for user review before signing. Each
 * message shows its operation (jetton transfer, NFT transfer, excess-gas refund, or plain
 * transfer), the real recipient, the forwarded TON amount, and the raw BOC payload (copyable, so
 * the full value stays recoverable behind the middle-ellipsis).
 *
 * @param messages decoded messages, built by [com.vultisig.wallet.ui.models.keysign.mapTonMessages]
 * @param initiallyExpanded preview-only override for the collapsed/expanded state; defaults to
 *   collapsed so production behaviour is unchanged
 */
@Composable
internal fun SignTonDisplayView(
    messages: List<TonMessageUiModel>,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    if (messages.isEmpty()) return

    var isExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded },
        ) {
            Text(
                text = "${stringResource(R.string.sign_ton_messages)} (${messages.size})",
                style = Theme.brockmann.button.medium.regular,
                color = Theme.v2.colors.text.tertiary,
            )

            UiIcon(
                drawableResId = R.drawable.chevron,
                tint = Theme.v2.colors.neutrals.n100,
                size = 8.dp,
                modifier = Modifier.graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .background(
                            color = Theme.v2.colors.variables.bordersLight,
                            shape = RoundedCornerShape(12.dp),
                        )
                        .padding(8.dp)
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                messages.forEachIndexed { index, message ->
                    TonMessageRow(message = message, index = index)
                }
            }
        }
    }
}

@Composable
private fun TonMessageRow(message: TonMessageUiModel, index: Int) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .background(
                    shape = RoundedCornerShape(8.dp),
                    color = Theme.v2.colors.backgrounds.dark,
                )
                .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "#${index + 1}",
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.tertiary,
                fontSize = 11.sp,
            )
            Text(
                text = stringResource(message.operation.titleRes),
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.primary,
                fontSize = 11.sp,
            )
            if (message.hasStateInit) {
                BadgeText(text = stringResource(R.string.sign_ton_state_init))
            }
        }

        message.recipient?.let { recipient ->
            TonDetailRow(
                label = stringResource(R.string.verify_transaction_to_title),
                value = recipient,
                copyableValue = recipient,
                monospace = true,
            )
        }
        message.amount?.let { amount ->
            TonDetailRow(label = stringResource(message.operation.amountLabelRes), value = amount)
        }
        message.rawPayload?.let { payload ->
            TonDetailRow(
                label = stringResource(R.string.ton_raw_payload),
                value = payload,
                copyableValue = payload,
                monospace = true,
            )
        }
    }
}

@Composable
private fun TonDetailRow(
    label: String,
    value: String,
    copyableValue: String? = null,
    monospace: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = Theme.brockmann.button.medium.medium,
            color = Theme.v2.colors.text.tertiary,
            fontSize = 10.sp,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
            textAlign = TextAlign.End,
        )
        if (copyableValue != null) {
            CopyIcon(textToCopy = copyableValue, size = 14.dp)
        }
    }
}

@Composable
private fun BadgeText(text: String) {
    Text(
        text = text,
        modifier =
            Modifier.background(
                    color = Theme.v2.colors.variables.bordersLight,
                    shape = RoundedCornerShape(4.dp),
                )
                .padding(horizontal = 6.dp, vertical = 2.dp),
        color = Theme.v2.colors.text.primary,
        style = Theme.brockmann.button.medium.medium,
        fontSize = 9.sp,
    )
}

private val TonMessageOperation.titleRes: Int
    get() =
        when (this) {
            TonMessageOperation.Swap -> R.string.ton_op_swap
            TonMessageOperation.JettonTransfer -> R.string.ton_op_jetton_transfer
            TonMessageOperation.NftTransfer -> R.string.ton_op_nft_transfer
            TonMessageOperation.ExcessGasRefund -> R.string.ton_op_excess_gas_refund
            TonMessageOperation.Transfer -> R.string.ton_op_transfer
        }

private val TonMessageOperation.amountLabelRes: Int
    get() =
        when (this) {
            TonMessageOperation.JettonTransfer,
            TonMessageOperation.NftTransfer -> R.string.ton_forward_ton_amount
            TonMessageOperation.Swap,
            TonMessageOperation.ExcessGasRefund,
            TonMessageOperation.Transfer -> R.string.verify_transaction_amount_title
        }

@Preview
@Composable
private fun PreviewSignTonDisplay() {
    SignTonDisplayView(
        messages =
            listOf(
                TonMessageUiModel(
                    operation = TonMessageOperation.JettonTransfer,
                    recipient = "EQDrLq9I7m6lvP6zUGZqJ8r4y0sP3pQ1n2vWk5tXcB9aZ7eF",
                    amount = "0.001 TON",
                    rawPayload = "te6cckEBAQEAWQAArg+KfqUAAAAAAAAwOUBfXhAIAf...",
                    hasStateInit = false,
                ),
                TonMessageUiModel(
                    operation = TonMessageOperation.Transfer,
                    recipient = "EQAB1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                    amount = "0.32 TON",
                    rawPayload = null,
                    hasStateInit = true,
                ),
            )
    )
}
