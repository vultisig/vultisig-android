package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

/**
 * Card used on custom-message Verify and Transaction Complete screens to display a labelled value
 * (e.g. Method, Message, Signature). Renders a bordered, dark-filled rounded card with a muted
 * caption label and a primary-color value.
 */
@Composable
internal fun SignMessageCard(title: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(12.dp),
                )
                .background(
                    color = Theme.v2.colors.backgrounds.disabled,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            color = Theme.v2.colors.text.tertiary,
            style = Theme.brockmann.supplementary.caption,
        )

        Text(
            text = value,
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.body.s.regular,
        )
    }
}
