package com.vultisig.wallet.ui.screens.send

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.CopyIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.models.keysign.DecodedFunctionParam
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString

/**
 * Renders a list of [DecodedFunctionParam] rows produced by
 * [com.vultisig.wallet.ui.models.keysign.decodedFunctionParams].
 *
 * Each row mirrors the layout of [com.vultisig.wallet.ui.screens.swap.VerifyCardDetails] — label on
 * the left, value end-aligned on the right — with two additions: a `CopyIcon` next to address
 * values so the user can recover the full hex even though the visible string is middle-ellipsised,
 * and an optional `secondary` tag (e.g. `Uniswap V3 Router` for known DEX addresses) under the
 * value. The warning colour is preserved for unlimited-approval amount rows so the dangerous case
 * stays visually consistent with the inline approval banner.
 */
@Composable
internal fun DecodedFunctionParamRows(
    params: List<DecodedFunctionParam>,
    modifier: Modifier = Modifier,
    onCopy: (String) -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier.fillMaxWidth()) {
        params.forEach { DecodedFunctionParamRow(it, onCopy = onCopy) }
    }
}

@Composable
private fun DecodedFunctionParamRow(param: DecodedFunctionParam, onCopy: (String) -> Unit) {
    val valueColor =
        if (param.isWarning) Theme.v2.colors.alerts.warning else Theme.v2.colors.text.primary
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Top,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = param.label.asString(),
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
            maxLines = 1,
        )

        UiSpacer(weight = 1f)

        Column(
            horizontalAlignment = Alignment.End,
            // Cap the value column so it always leaves room for the label. Without the cap, a long
            // ellipsised address can push the label off-screen when the row reaches its min width.
            modifier = Modifier.fillMaxWidth(fraction = 0.7f),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = param.value.asString(),
                    style = Theme.brockmann.body.s.medium,
                    color = valueColor,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.MiddleEllipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )

                param.copyableValue
                    ?.takeIf { it.isNotBlank() }
                    ?.let { copyable ->
                        CopyIcon(
                            textToCopy = copyable,
                            size = 14.dp,
                            tint = Theme.v2.colors.text.tertiary,
                            onCopyCompleted = onCopy,
                        )
                    }
            }

            param.secondary
                ?.takeIf { it.isNotBlank() }
                ?.let { secondary ->
                    Text(
                        text = secondary,
                        style = Theme.brockmann.supplementary.footnote,
                        color = Theme.v2.colors.text.tertiary,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.MiddleEllipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
        }
    }
}

@Preview
@Composable
private fun DecodedFunctionParamRowsPreview() {
    DecodedFunctionParamRows(
        params =
            listOf(
                DecodedFunctionParam(
                    label = UiText.DynamicString("Spender"),
                    value = UiText.DynamicString("0xE592427A0AEce92De3Edee1F18E0157C05861564"),
                    copyableValue = "0xE592427A0AEce92De3Edee1F18E0157C05861564",
                    secondary = "Uniswap V3 Router",
                ),
                DecodedFunctionParam(
                    label = UiText.DynamicString("Amount"),
                    value = UiText.DynamicString("Unlimited USDC"),
                    isWarning = true,
                ),
            )
    )
}
