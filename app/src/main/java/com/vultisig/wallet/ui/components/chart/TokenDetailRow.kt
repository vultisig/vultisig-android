package com.vultisig.wallet.ui.components.chart

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.texts.LoadableValue
import com.vultisig.wallet.ui.theme.Theme

/**
 * One label/value line of a [TokenDetailSection]: label on the left, value right-aligned, with an
 * optional caption under the value (the all-time high/low rows' "% · date"). Passing [onClick]
 * makes the whole row the tap target rather than just its trailing content.
 */
@Composable
internal fun TokenDetailRow(
    label: String,
    modifier: Modifier = Modifier,
    value: String? = null,
    subValue: String? = null,
    isValueVisible: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .let { if (onClick != null) it.clickable(onClick = onClick) else it }
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Weighted rather than followed by a spacer: with the row's own 12dp arrangement, a
        // weighted spacer would put that gap on both sides of it and double the separation.
        Text(
            text = label,
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
            modifier = Modifier.weight(1f),
        )

        if (trailing != null) {
            trailing()
        } else {
            Column(horizontalAlignment = Alignment.End) {
                LoadableValue(
                    value = value,
                    isVisible = isValueVisible,
                    style = Theme.satoshi.price.bodyS,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.End,
                )
                if (subValue != null) {
                    UiSpacer(size = 2.dp)
                    Text(
                        text = subValue,
                        style = Theme.brockmann.supplementary.captionSmall,
                        color = Theme.v2.colors.text.tertiary,
                        textAlign = TextAlign.End,
                    )
                }
            }
        }
    }
}
