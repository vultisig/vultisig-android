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
import com.vultisig.wallet.data.models.payload.SignTon
import com.vultisig.wallet.data.models.payload.TonMessage
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.serialization.json.Json

@Composable
fun SignTonDisplayView(signTon: String, modifier: Modifier = Modifier) {
    val messages =
        remember(signTon) {
            runCatching { Json.decodeFromString<SignTon>(signTon).messages }
                .getOrDefault(emptyList())
        }
    if (messages.isEmpty()) return

    var isExpanded by remember { mutableStateOf(false) }

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
                text = "${stringResource(R.string.sign_ton_messages)} (${messages.size})",
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

            if (message.payload.isNotEmpty()) {
                BadgeText(text = stringResource(R.string.sign_ton_payload))
            }
            if (message.stateInit.isNotEmpty()) {
                BadgeText(text = stringResource(R.string.sign_ton_state_init))
            }
        }

        Text(
            text = message.toAddress,
            color = Theme.v2.colors.neutrals.n100,
            style = Theme.brockmann.button.medium.medium,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )

        Text(
            text = formatNanotons(message.toAmount),
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

private fun formatNanotons(nano: Long): String {
    val whole = nano / 1_000_000_000L
    val frac = (nano % 1_000_000_000L).toString().padStart(9, '0').trimEnd('0')
    return if (frac.isEmpty()) "$whole TON" else "$whole.$frac TON"
}

@Preview
@Composable
private fun PreviewSignTonDisplaySingle() {
    SignTonDisplayView(
        signTon =
            Json.encodeToString(
                SignTon(
                    messages =
                        listOf(
                            TonMessage(
                                toAddress = "EQAB1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                                toAmount = 1_500_000_000L,
                            )
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
            Json.encodeToString(
                SignTon(
                    messages =
                        listOf(
                            TonMessage(
                                toAddress = "EQAB1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                                toAmount = 1_500_000_000L,
                                payload = "te6ccg...",
                            ),
                            TonMessage(
                                toAddress = "EQAB0000000000ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                                toAmount = 250_000_000L,
                                payload = "te6...",
                                stateInit = "te6state...",
                            ),
                            TonMessage(
                                toAddress = "EQAB9999999999ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                                toAmount = 100_000L,
                            ),
                            TonMessage(
                                toAddress = "EQAB7777777777ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghij",
                                toAmount = 5_000_000_000L,
                                stateInit = "te6stateinit...",
                            ),
                        )
                )
            )
    )
}
