package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.presenter.common.clickOnce
import com.vultisig.wallet.ui.theme.Theme


@Composable
internal fun VaultActionButton(
    text: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        color = color,
        style = Theme.menlo.subtitle2,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Theme.colors.turquoise600Main,
                        Theme.colors.persianBlue600Main
                    )
                ),
                shape = RoundedCornerShape(16.dp),
            )
            .background(Theme.colors.oxfordBlue400)
            .padding(
                vertical = 8.dp,
                horizontal = 16.dp,
            )
            .clickOnce (onClick = onClick),
    )
}

@Composable
@Preview
private fun VaultActionPreview() {
    Row {
        VaultActionButton(
            "SEND",
            Theme.colors.turquoise600Main,
            {}
        )
    }
}

