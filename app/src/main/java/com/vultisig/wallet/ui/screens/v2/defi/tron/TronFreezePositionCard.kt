package com.vultisig.wallet.ui.screens.v2.defi.tron

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.library.UiPlaceholderLoader
import com.vultisig.wallet.ui.theme.Theme

private val TronFreezeCardIconCircleColor = Color.White.copy(alpha = 0.12f)
private val TronFreezeButtonBevelTopColor = Color.White.copy(alpha = 0.10f)
private val TronFreezeButtonBevelBottomColor = Color(0xFF0F1C3E)

/**
 * Strokes the pill perimeter with a vertical gradient (highlight → transparent → shadow) so the
 * bevel reads as a soft top shine fading to a dark bottom edge, matching Figma `inset` shadows `0
 * 1px 1px 0 rgba(255,255,255,0.10)` and `0 -1px 0.5px 0 #0F1C3E` without hard clip seams on the
 * side curves.
 */
private fun Modifier.tronFreezeButtonBevel(enabled: Boolean): Modifier = drawWithContent {
    drawContent()
    val alphaMultiplier = if (enabled) 1f else 0.5f
    val strokePx = 1.dp.toPx()
    val cornerRadius = CornerRadius(size.height / 2f)
    val shapePath = Path().apply { addRoundRect(RoundRect(Rect(Offset.Zero, size), cornerRadius)) }
    val bevelBrush =
        Brush.verticalGradient(
            colorStops =
                arrayOf(
                    0f to
                        TronFreezeButtonBevelTopColor.copy(
                            alpha = TronFreezeButtonBevelTopColor.alpha * alphaMultiplier
                        ),
                    0.5f to Color.Transparent,
                    1f to TronFreezeButtonBevelBottomColor.copy(alpha = alphaMultiplier),
                )
        )
    clipPath(shapePath) {
        drawRoundRect(
            brush = bevelBrush,
            topLeft = Offset.Zero,
            size = size,
            cornerRadius = cornerRadius,
            style = Stroke(width = strokePx * 2f),
        )
    }
}

/**
 * Card showing frozen TRX balance with Freeze/Unfreeze actions; shows placeholders and disables
 * buttons when [isLoading].
 */
@Composable
internal fun TronFreezePositionCard(
    frozenTotalPrice: String,
    frozenTotalTrx: String,
    isBalanceVisible: Boolean,
    isLoading: Boolean = false,
    isUnfreezeEnabled: Boolean,
    isFreezeEnabled: Boolean = true,
    onClickFreeze: () -> Unit,
    onClickUnfreeze: () -> Unit,
) {
    Column(
        modifier =
            Modifier.fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Theme.v2.colors.backgrounds.secondary)
                .border(1.dp, Theme.v2.colors.border.normal, RoundedCornerShape(16.dp))
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Header: logo + title + amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UiIcon(drawableResId = R.drawable.tron, size = 42.dp)

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.tron_defi_tron_freeze),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
                if (isLoading) {
                    Box(modifier = Modifier.width(120.dp)) {
                        Text(
                            text = "–",
                            style = Theme.brockmann.headings.title1,
                            color = Color.Transparent,
                        )
                        UiPlaceholderLoader(modifier = Modifier.matchParentSize())
                    }
                } else {
                    Text(
                        text = if (isBalanceVisible) frozenTotalPrice else HIDE_BALANCE_CHARS,
                        style = Theme.brockmann.headings.title1,
                        color = Theme.v2.colors.text.primary,
                    )
                }
            }
        }

        HorizontalDivider(color = Theme.v2.colors.border.light, thickness = 1.dp)

        // Frozen amount + action buttons
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = stringResource(R.string.tron_defi_frozen),
                    style = Theme.brockmann.body.s.medium,
                    color = Theme.v2.colors.text.tertiary,
                )
                if (isLoading) {
                    Box(modifier = Modifier.width(80.dp)) {
                        Text(
                            text = "–",
                            style = Theme.brockmann.headings.title3,
                            color = Color.Transparent,
                        )
                        UiPlaceholderLoader(modifier = Modifier.matchParentSize())
                    }
                } else {
                    Text(
                        text = if (isBalanceVisible) "$frozenTotalTrx TRX" else HIDE_BALANCE_CHARS,
                        style = Theme.brockmann.headings.title3,
                        color = Theme.v2.colors.text.primary,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TronFreezeActionButton(
                    title = stringResource(R.string.tron_defi_unfreeze),
                    icon = R.drawable.circle_minus,
                    background = Theme.v2.colors.backgrounds.tertiary_2,
                    border = BorderStroke(Dp.Hairline, Color.White.copy(alpha = 0.03f)),
                    contentColor = Theme.v2.colors.text.primary,
                    iconCircleColor = TronFreezeCardIconCircleColor,
                    enabled = !isLoading && isUnfreezeEnabled,
                    modifier = Modifier.weight(1f),
                    onClick = onClickUnfreeze,
                )
                TronFreezeActionButton(
                    title = stringResource(R.string.tron_defi_freeze),
                    icon = R.drawable.circle_plus,
                    background = Theme.v2.colors.buttons.ctaPrimary,
                    border = BorderStroke(Dp.Hairline, Theme.v2.colors.primary.accent3),
                    contentColor = Theme.v2.colors.text.primary,
                    iconCircleColor = TronFreezeCardIconCircleColor,
                    enabled = isFreezeEnabled && !isLoading,
                    modifier = Modifier.weight(1f),
                    onClick = onClickFreeze,
                )
            }
        }
    }
}

@Composable
private fun TronFreezeActionButton(
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
        shape = RoundedCornerShape(50),
        contentPadding = PaddingValues(start = 4.dp, top = 6.dp, end = 16.dp, bottom = 6.dp),
        modifier = modifier.height(46.dp).tronFreezeButtonBevel(enabled = enabled),
    ) {
        Box(
            modifier =
                Modifier.size(34.dp)
                    .background(
                        if (enabled) iconCircleColor else iconCircleColor.copy(alpha = 0.5f),
                        RoundedCornerShape(50),
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

@Preview(showBackground = true)
@Composable
private fun TronFreezePositionCardPreview() {
    Box(modifier = Modifier.background(Theme.v2.colors.backgrounds.primary).padding(16.dp)) {
        TronFreezePositionCard(
            frozenTotalPrice = "$4,800",
            frozenTotalTrx = "800.000000",
            isBalanceVisible = true,
            isUnfreezeEnabled = true,
            onClickFreeze = {},
            onClickUnfreeze = {},
        )
    }
}
