package com.vultisig.wallet.ui.screens.qbtc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.theme.Theme

/** Height of the Figma reference frame (361x156), which the coin geometry below is measured in. */
private val BannerHeight = 156.dp

private val GlassCloseSize = 40.dp
private val GlassCloseInset = 9.dp
private val GlassCloseBlur = 20.dp
private val GlassCloseIconSize = 12.dp

private val ContentPadding = 24.dp

/**
 * Kept clear of the close control on both sides, so the subtitle stays centred on the card: it is
 * the one line drawn level with the control, and a long translation would otherwise run under the
 * frost disc.
 */
private val SubtitleGutter = GlassCloseInset + GlassCloseSize - ContentPadding

@Composable
internal fun ClaimQbtcPromoBanner(
    onClaim: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glow = Theme.v2.colors.primary.accent2
    val scrim = Theme.v2.colors.backgrounds.background
    val shine = Theme.v2.colors.neutrals.n50
    val backdrop = rememberGraphicsLayer()
    val frost = rememberGraphicsLayer()

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .height(BannerHeight)
                .clip(Theme.v2.radius.xl)
                .background(Theme.v2.colors.backgrounds.surface2)
                .drawBehind {
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f

                    // The card's depth, in the order Figma stacks it: a faint disc wider than the
                    // card, a dark navy disc that sinks its middle, and the brand glow on top.
                    drawCircle(
                        color = shine.copy(alpha = 0.014f),
                        radius = 209.dp.toPx(),
                        center = Offset(centerX, centerY),
                    )
                    drawCircle(
                        color = scrim.copy(alpha = 0.7f),
                        radius = 147.dp.toPx(),
                        center = Offset(centerX, centerY),
                    )
                    drawCircle(
                        color = shine.copy(alpha = 0.07f),
                        radius = 146.dp.toPx(),
                        center = Offset(centerX, centerY),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                0f to glow.copy(alpha = 0.7f),
                                1f to scrim.copy(alpha = 0f),
                                center = Offset(centerX, 115.dp.toPx()),
                                radius = 175.dp.toPx(),
                            )
                    )
                }
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = Theme.v2.radius.xl,
                ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.matchParentSize().drawWithContent {
                    backdrop.record { this@drawWithContent.drawContent() }
                    drawLayer(backdrop)
                },
        ) {
            QbtcCoinDecorations()

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxWidth().padding(ContentPadding),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.qbtc_claim_banner_subtitle),
                        style = Theme.brockmann.supplementary.caption,
                        color = Theme.v2.colors.text.tertiary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = SubtitleGutter),
                    )
                    Text(
                        text = stringResource(R.string.qbtc_claim_banner_title),
                        style = Theme.brockmann.headings.title2,
                        color = Theme.v2.colors.text.primary,
                        textAlign = TextAlign.Center,
                    )
                }
                Text(
                    text = stringResource(R.string.qbtc_claim_banner_cta),
                    style = Theme.brockmann.supplementary.caption,
                    color = Theme.v2.colors.text.button.primary,
                    modifier =
                        Modifier.clip(Theme.v2.radius.pill)
                            .background(Theme.v2.colors.buttons.ctaPrimary)
                            .clickable(onClick = onClaim)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        Box(
            modifier =
                Modifier.matchParentSize().drawBehind {
                    drawGlassCloseBackdrop(backdrop = backdrop, frost = frost, tint = shine)
                }
        )

        // The icon carries no description of its own, so the control announces once, as a button.
        val dismissLabel = stringResource(R.string.close_sheet_content_description)
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier.align(Alignment.TopEnd)
                    .padding(all = GlassCloseInset)
                    .size(GlassCloseSize)
                    .clip(CircleShape)
                    .clickOnce(onClick = onDismiss)
                    .semantics {
                        role = Role.Button
                        contentDescription = dismissLabel
                    },
        ) {
            UiIcon(
                drawableResId = R.drawable.glass,
                tint = Theme.v2.colors.neutrals.n100,
                size = GlassCloseIconSize,
            )
        }
    }
}

/**
 * Frosts the card under the close control: what the banner drew is re-drawn blurred and clipped to
 * the control's circle, so the coin behind it reads as glass rather than as a bare icon.
 *
 * [backdrop] holds the card's own drawing, recorded a moment earlier in the same frame; [frost]
 * carries the blur, since a render effect belongs to a layer rather than to a draw call. The blur
 * needs API 31 — below it the layer draws unblurred, leaving the control as it was before.
 */
private fun DrawScope.drawGlassCloseBackdrop(
    backdrop: GraphicsLayer,
    frost: GraphicsLayer,
    tint: Color,
) {
    val blurRadius = GlassCloseBlur.toPx()
    frost.renderEffect = BlurEffect(radiusX = blurRadius, radiusY = blurRadius)
    frost.record { drawLayer(backdrop) }

    val radius = GlassCloseSize.toPx() / 2f
    val center =
        Offset(
            x = size.width - GlassCloseInset.toPx() - radius,
            y = GlassCloseInset.toPx() + radius,
        )

    clipPath(Path().apply { addOval(Rect(center = center, radius = radius)) }) {
        drawLayer(frost)
        drawCircle(color = tint.copy(alpha = 0.01f), radius = radius, center = center)
    }
}

/**
 * The scattered coin stacks, placed from the Figma frame: left-hand coins keep their distance from
 * the leading edge and right-hand ones from the trailing edge, so the arrangement survives a card
 * wider or narrower than the 361 dp reference.
 *
 * Each coin is sized unrotated — [rotate] spins it about its centre without changing the space it
 * takes — so these are Figma's inner sizes, not its rotated bounding boxes.
 */
@Composable
private fun QbtcCoinDecorations() {
    Box(modifier = Modifier.fillMaxSize()) {
        QbtcCoin(
            width = 37.3.dp,
            height = 41.7.dp,
            rotation = -26.62f,
            alignment = Alignment.TopStart,
            offsetX = 5.4.dp,
            offsetY = 5.2.dp,
        )
        QbtcCoin(
            width = 80.dp,
            height = 90.dp,
            rotation = 13.22f,
            alignment = Alignment.TopStart,
            offsetX = (-28.8).dp,
            offsetY = 43.dp,
        )
        QbtcCoin(
            width = 44.1.dp,
            height = 49.2.dp,
            rotation = 8.42f,
            alignment = Alignment.TopStart,
            offsetX = 1.4.dp,
            offsetY = 124.dp,
        )
        QbtcCoin(
            width = 86.8.dp,
            height = 97.8.dp,
            rotation = 0f,
            alignment = Alignment.TopEnd,
            offsetX = 13.8.dp,
            offsetY = 11.dp,
        )
        QbtcCoin(
            width = 43.dp,
            height = 48.dp,
            rotation = -6.84f,
            alignment = Alignment.TopEnd,
            offsetX = (-27.3).dp,
            offsetY = 106.4.dp,
        )
    }
}

@Composable
private fun BoxScope.QbtcCoin(
    width: Dp,
    height: Dp,
    rotation: Float,
    alignment: Alignment,
    offsetX: Dp,
    offsetY: Dp,
) {
    Image(
        painter = painterResource(R.drawable.qbtc),
        contentDescription = null,
        contentScale = ContentScale.FillBounds,
        modifier =
            Modifier.align(alignment)
                .offset(x = offsetX, y = offsetY)
                .size(width, height)
                .rotate(rotation),
    )
}

@Composable
internal fun ClaimQbtcBottomCta(onClaim: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier =
            modifier
                .fillMaxWidth()
                .height(120.dp)
                .background(
                    brush =
                        Brush.verticalGradient(
                            colors =
                                listOf(Color.Transparent, Theme.v2.colors.backgrounds.background)
                        )
                ),
    ) {
        Text(
            text = stringResource(R.string.qbtc_claim_title),
            style = Theme.brockmann.button.semibold.medium,
            color = Theme.v2.colors.text.primary,
            textAlign = TextAlign.Center,
            modifier =
                Modifier.fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp)
                    .clip(Theme.v2.radius.pill)
                    .background(Theme.v2.colors.buttons.ctaPrimary)
                    .clickable(onClick = onClaim)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

@Preview
@Composable
private fun ClaimQbtcPromoBannerPreview() {
    ClaimQbtcPromoBanner(onClaim = {}, onDismiss = {}, modifier = Modifier.padding(16.dp))
}
