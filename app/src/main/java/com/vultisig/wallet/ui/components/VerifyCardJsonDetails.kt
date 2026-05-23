package com.vultisig.wallet.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun VerifyCardJsonDetails(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    subtitleColor: Color = Theme.v2.colors.alerts.info,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        Text(
            text = title,
            style = Theme.brockmann.supplementary.footnote,
            color = Theme.v2.colors.text.tertiary,
            maxLines = 1,
        )

        Text(
            text = subtitle,
            style = Theme.brockmann.body.m.medium.copy(fontFamily = FontFamily.Monospace),
            color = subtitleColor,
            textAlign = TextAlign.Start,
            modifier = Modifier.horizontalScroll(rememberScrollState()),
        )
    }
}
