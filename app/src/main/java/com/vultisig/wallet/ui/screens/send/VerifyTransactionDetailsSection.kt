package com.vultisig.wallet.ui.screens.send

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.models.keysign.DecodedFunctionParam
import com.vultisig.wallet.ui.screens.swap.VerifyCardJsonDetails
import com.vultisig.wallet.ui.theme.Theme

/**
 * Expandable row that surfaces a decoded EVM function call. The header is the whole-row tap target
 * (WCAG 2.5.5) and toggles a bordered card containing either the raw function signature + decoded
 * argument rows, or the raw JSON arguments when no decoder matched.
 */
@Composable
internal fun TransactionDetailsSection(
    functionSignature: String?,
    functionInputs: String?,
    decodedFunctionParams: List<DecodedFunctionParam>?,
    initiallyExpanded: Boolean = false,
) {
    var isExpanded by rememberSaveable { mutableStateOf(initiallyExpanded) }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Whole row is the tap target so the WCAG 2.5.5 minimum (48dp) is met without enlarging
        // the visual chevron, and TalkBack announces "Transaction details, button, expanded /
        // collapsed" instead of two separate nodes.
        val expandLabel = stringResource(R.string.tx_done_transaction_details)
        val toggleLabel =
            stringResource(
                if (isExpanded) R.string.tx_done_transaction_details_collapse
                else R.string.tx_done_transaction_details_expand
            )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier =
                Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = toggleLabel,
                ) {
                    isExpanded = !isExpanded
                },
        ) {
            Text(
                text = expandLabel,
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.primary,
            )

            UiIcon(
                drawableResId = R.drawable.chevron,
                tint = Theme.v2.colors.text.tertiary,
                size = 8.dp,
                modifier = Modifier.graphicsLayer(rotationZ = if (isExpanded) 180f else 0f),
            )
        }

        AnimatedVisibility(visible = isExpanded) {
            // Inner column intentionally lets content size naturally; the outer scaffold's
            // verticalScroll handles long JSON. A nested verticalScroll here trapped the last
            // lines of long signatures/inputs out of reach.
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(top = 12.dp)
                        .border(1.dp, Theme.v2.colors.border.normal, RoundedCornerShape(12.dp))
                        .padding(16.dp),
            ) {
                functionSignature?.let {
                    VerifyCardJsonDetails(
                        title =
                            stringResource(R.string.verify_transaction_function_signature_title),
                        subtitle = it,
                    )
                }

                // When the parser produces labelled rows we show them in place of the raw JSON —
                // semantic labels read better than `["0xabc…", "115792…"]`. Fall back to the raw
                // JSON when the function signature is something the parser doesn't recognise so
                // the user still sees the underlying arguments rather than nothing.
                if (!decodedFunctionParams.isNullOrEmpty()) {
                    DecodedFunctionParamRows(params = decodedFunctionParams)
                } else {
                    functionInputs?.let {
                        VerifyCardJsonDetails(
                            title =
                                stringResource(R.string.verify_transaction_function_inputs_title),
                            subtitle = it,
                        )
                    }
                }
            }
        }
    }
}
