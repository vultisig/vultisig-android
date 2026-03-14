@file:OptIn(ExperimentalMaterial3Api::class)

package com.vultisig.wallet.ui.screens.agent

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.bottomsheets.V2BottomSheet
import com.vultisig.wallet.ui.theme.Theme

private val DashedBorderColor = Color(0xFF4879FD).copy(alpha = 0.55f)
private val CardBg = Color(0xFF02122B).copy(alpha = 0.5f)

private val ChipBlue = Color(0xFF4879FD).copy(alpha = 0.10f)
private val ChipGreen = Color(0xFF13C89D).copy(alpha = 0.05f)
private val ChipOrange = Color(0xFFFFA500).copy(alpha = 0.10f)
private val ChipBorder = Color.White.copy(alpha = 0.03f)

// Figma: card h=251, positioned at top=-53 → 53px clipped from top
private val CardHeight = 251.dp
private val CardWidth = 320.dp
private val CardClipAmount = 53.dp
private val CardVisibleHeight = CardHeight - CardClipAmount // 198dp

@Composable
internal fun AgentAuthorizeBottomSheet(
    onAuthorize: () -> Unit,
    onNotNow: () -> Unit,
    onLearnMore: () -> Unit,
    onDismissRequest: () -> Unit,
) {
    V2BottomSheet(onDismissRequest = onDismissRequest) {
        AgentAuthorizeBottomSheetContent(
            onAuthorize = onAuthorize,
            onNotNow = onNotNow,
            onLearnMore = onLearnMore,
        )
    }
}

@Composable
private fun AgentAuthorizeBottomSheetContent(
    onAuthorize: () -> Unit,
    onNotNow: () -> Unit,
    onLearnMore: () -> Unit, // this page is not yet implemented, will come later according to Mia
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        UiSpacer(size = 22.dp)

        // Card area: a clipping box that hides the top portion of the card
        Box(
            modifier = Modifier.height(CardVisibleHeight).clipToBounds(),
            contentAlignment = Alignment.TopCenter,
        ) {
            // The actual card — shifted up so top 53dp is clipped
            Box(
                modifier =
                    Modifier.width(CardWidth)
                        .height(CardHeight)
                        .offset(y = -CardClipAmount)
                        .clip(RoundedCornerShape(24.dp))
                        .background(CardBg)
                        .dashedRoundRect(
                            color = DashedBorderColor,
                            cornerRadius = 24.dp,
                            dashLength = 6.dp,
                            gapLength = 4.dp,
                            strokeWidth = 1.dp,
                        )
                        .padding(16.dp),
                contentAlignment = Alignment.BottomStart,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 275.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AgentPreviewChip(
                        text = stringResource(R.string.agent_starter_plugins),
                        icon = R.drawable.ic_agent_chip_plugins,
                        backgroundColor = ChipBlue,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AgentPreviewChip(
                            text = stringResource(R.string.agent_starter_earn_apy),
                            icon = R.drawable.ic_agent_chip_earn,
                            backgroundColor = ChipBlue,
                        )
                        AgentPreviewChip(
                            text = stringResource(R.string.agent_starter_send),
                            icon = R.drawable.ic_agent_chip_send,
                            backgroundColor = ChipGreen,
                        )
                    }
                    AgentPreviewChip(
                        text = stringResource(R.string.agent_starter_swap),
                        icon = R.drawable.ic_agent_chip_swap,
                        backgroundColor = ChipOrange,
                    )
                }
            }
        }

        UiSpacer(size = 24.dp)

        // Title & description
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.agent_authorize_title),
                style = Theme.brockmann.headings.title3,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.agent_authorize_description),
                style = Theme.brockmann.body.s.medium,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        UiSpacer(size = 32.dp)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VsButton(
                label = stringResource(R.string.not_now),
                variant = VsButtonVariant.Secondary,
                onClick = onNotNow,
                modifier = Modifier.weight(1f),
            )
            VsButton(
                label = stringResource(R.string.agent_authorize_button),
                variant = VsButtonVariant.CTA,
                onClick = onAuthorize,
                modifier = Modifier.weight(1f),
            )
        }

        UiSpacer(size = 20.dp)

        Text(
            text = stringResource(R.string.agent_learn_more),
            style = Theme.brockmann.body.s.medium,
            color = Theme.v2.colors.text.tertiary,
            textAlign = TextAlign.Center,
            textDecoration = TextDecoration.Underline,
            modifier = Modifier.fillMaxWidth(),
        )

        UiSpacer(size = 16.dp)
    }
}

@Composable
private fun AgentPreviewChip(
    text: String,
    @DrawableRes icon: Int,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(9.dp))
                .background(backgroundColor)
                .border(1.dp, ChipBorder, RoundedCornerShape(9.dp))
                .padding(horizontal = 9.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.Unspecified,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = text,
            style =
                TextStyle(
                    fontFamily = Theme.brockmann.supplementary.captionSmall.fontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                ),
            color = Theme.v2.colors.text.primary,
        )
    }
}

/** Full dashed rounded rectangle border on all 4 sides. */
private fun Modifier.dashedRoundRect(
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp,
    gapLength: Dp,
    strokeWidth: Dp,
): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(cornerRadius.toPx()),
        style =
            Stroke(
                width = strokeWidth.toPx(),
                pathEffect =
                    PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f),
            ),
    )
}
