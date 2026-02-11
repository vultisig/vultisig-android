package com.vultisig.wallet.ui.screens.v2.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

@Composable
fun VsButton(
    modifier: Modifier = Modifier,
    label: String? = null,
    onClick: () -> Unit,
) {
    VsButton(
        modifier = modifier,
        onClick = onClick,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = Theme.brockmann.button.semibold.semibold,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}

@Composable
fun VsButton(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Theme.v2.colors.buttons.secondary,
    borderColor: Color = Theme.v2.colors.border.primaryAccent4,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(percent = 100),
    contentPadding: PaddingValues = PaddingValues(
        vertical = 14.dp,
        horizontal = 32.dp
    ),
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        modifier = modifier
            .clip(shape)
            .background(
                color = backgroundColor,
                shape = shape
            )
            .border(
                width = borderWidth,
                color = borderColor,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(contentPadding)
    ) {
        content()
    }
}

@Composable
fun VsButton(
    modifier: Modifier = Modifier,
    label: String,
    textStyle: TextStyle = Theme.brockmann.button.semibold.semibold,
    textColor: Color = Theme.v2.colors.text.primary,
    backgroundColor: Color = Theme.v2.colors.buttons.secondary,
    borderColor: Color = Theme.v2.colors.border.primaryAccent4,
    borderWidth: Dp = 1.dp,
    shape: Shape = RoundedCornerShape(percent = 100),
    contentPadding: PaddingValues = PaddingValues(
        vertical = 14.dp,
        horizontal = 32.dp
    ),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    VsButton(
        modifier = modifier,
        backgroundColor = backgroundColor,
        borderColor = borderColor,
        borderWidth = borderWidth,
        shape = shape,
        contentPadding = contentPadding,
        enabled = enabled,
        onClick = onClick,
    ) {
        Text(
            text = label,
            style = textStyle,
            color = textColor,
        )
    }
}