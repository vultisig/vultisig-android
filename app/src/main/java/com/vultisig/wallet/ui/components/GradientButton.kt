package com.vultisig.wallet.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun GradientButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = if (isSelected)
            Theme.v2.colors.backgrounds.primary
        else
            Theme.v2.colors.backgrounds.teal,
        style = Theme.montserrat.subtitle1,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clickable(onClick = onClick)
            .then(
                if (isSelected) {
                    Modifier.background(
                        brush = Brush.vultiGradient(),
                        shape = RoundedCornerShape(30.dp)
                    )
                } else {
                    Modifier.border(
                        width = 1.dp,
                        brush = Brush.vultiGradient(),
                        shape = RoundedCornerShape(30.dp),
                    )
                }
            )
            .padding(
                vertical = 12.dp,
                horizontal = 12.dp,
            ),
    )
}