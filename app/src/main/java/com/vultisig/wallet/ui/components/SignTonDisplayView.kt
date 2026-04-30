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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme
import java.math.BigInteger
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TonMessage

/**
 * Displays a collapsible list of TON transfer messages for user review before signing.
 *
 * @param signTon proto carrying one to four [vultisig.keysign.v1.TonMessage]s to display
 */
@Composable
fun SignTonDisplayView(signTon: SignTon, modifier: Modifier = Modifier) {
    val messages = signTon.tonMessages.filterNotNull()
    if (messages.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

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
                messages.forEachIndexed { index, msg ->
                    TonMessageRow(message = msg, index = index)
                }
            }
        }
    }
}

@Composable
private fun TonMessageRow(message: TonMessage, index: Int) {
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
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "#${index + 1}",
                style = Theme.brockmann.button.medium.medium,
                color = Theme.v2.colors.text.primary,
                fontSize = 11.sp,
            )

            if (!message.payload.isNullOrEmpty()) {
                BadgeText(text = stringResource(R.string.sign_ton_payload))
            }
            if (!message.stateInit.isNullOrEmpty()) {
                BadgeText(text = stringResource(R.string.sign_ton_state_init))
            }
        }

        Text(
            text = message.to,
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )

        Text(
            text = formatNanotons(message.amount),
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 11.sp,
        )
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

private fun formatNanotons(raw: String): String {
    val nano = raw.toBigIntegerOrNull() ?: return "Invalid TON amount"
    if (nano.signum() < 0) return "Invalid TON amount"
    val divisor = BigInteger.TEN.pow(9)
    val whole = nano / divisor
    val frac = (nano % divisor).toString().padStart(9, '0').trimEnd('0')
    return if (frac.isEmpty()) "$whole TON" else "$whole.$frac TON"
}

@Preview
@Composable
private fun PreviewSignTonDisplaySingle() {
    SignTonDisplayView(
        signTon =
            SignTon(
                tonMessages =
                    listOf(
                        TonMessage(
                            to = "EQAB1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                            amount = "1500000000",
                        )
                    )
            )
    )
}

@Preview
@Composable
private fun PreviewSignTonDisplayMulti() {
    SignTonDisplayView(
        signTon =
            SignTon(
                tonMessages =
                    listOf(
                        TonMessage(
                            to = "EQAB1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                            amount = "1500000000",
                            payload = "te6ccg...",
                        ),
                        TonMessage(
                            to = "EQAB0000000000ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                            amount = "250000000",
                            payload = "te6...",
                            stateInit = "te6state...",
                        ),
                        TonMessage(
                            to = "EQAB9999999999ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                            amount = "100000",
                        ),
                        TonMessage(
                            to = "EQAB7777777777ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                            amount = "5000000000",
                            stateInit = "te6stateinit...",
                        ),
                    )
            )
    )
}
