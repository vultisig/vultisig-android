package com.vultisig.wallet.ui.components.v2.containers

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.screens.send.FadingHorizontalDivider
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun TopShineContainer(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Theme.v2.colors.backgrounds.secondary,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier
            .fillMaxWidth(),
        shape = RoundedCornerShape(
            size = 12.dp
        ),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
    ) {
        FadingHorizontalDivider()
        content()
    }
}

@Preview
@Composable
private fun PreviewTopShineContainer() {
    TopShineContainer {
        Text(
            text = "top shine container",
            color = Theme.v2.colors.text.primary,
            style = Theme.brockmann.body.l.medium,
            modifier = Modifier.padding(16.dp)
        )
    }
}