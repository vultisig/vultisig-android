package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

internal val TronDeFiActionButtonIconCircleColor = Color.White.copy(alpha = 0.12f)

/** Pill action button used by the TRON DeFi cards: circled icon on the left, centered label. */
@Composable
internal fun TronDeFiActionButton(
    title: String,
    icon: Int,
    background: Color,
    border: BorderStroke,
    contentColor: Color,
    iconCircleColor: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val resolvedBorder =
        if (enabled) {
            border
        } else {
            BorderStroke(
                width = border.width,
                color =
                    when (val brush = border.brush) {
                        is SolidColor -> brush.value.copy(alpha = 0.5f)
                        else -> Color.Gray.copy(alpha = 0.5f)
                    },
            )
        }
    Button(
        onClick = onClick,
        enabled = enabled,
        colors =
            ButtonDefaults.buttonColors(
                containerColor = background,
                contentColor = contentColor,
                disabledContainerColor = background.copy(alpha = 0.5f),
                disabledContentColor = contentColor.copy(alpha = 0.5f),
            ),
        border = resolvedBorder,
        shape = Theme.v2.radius.pill,
        contentPadding = PaddingValues(start = 4.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
        modifier = modifier.height(46.dp),
    ) {
        Box(
            modifier =
                Modifier.size(34.dp)
                    .background(
                        if (enabled) iconCircleColor else iconCircleColor.copy(alpha = 0.5f),
                        Theme.v2.radius.pill,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(id = icon),
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
            )
        }
        UiSpacer(5.dp)
        Text(
            text = title,
            style = Theme.brockmann.button.medium.medium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}
