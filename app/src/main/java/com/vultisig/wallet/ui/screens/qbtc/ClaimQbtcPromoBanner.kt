package com.vultisig.wallet.ui.screens.qbtc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Theme

@Composable
internal fun ClaimQbtcPromoBanner(onClaim: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxWidth()
                .height(156.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Theme.v2.colors.backgrounds.surface2)
                .drawBehind {
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                0f to QbtcBannerGlow.copy(alpha = 0.3f),
                                0.45f to QbtcBannerGlow.copy(alpha = 0.1f),
                                1f to Color.Transparent,
                                center = Offset(size.width / 2f, size.height / 2f + 30.dp.toPx()),
                                radius = size.maxDimension * 0.4f,
                            )
                    )
                },
    ) {
        QbtcCoinDecorations()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(24.dp),
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
                style = Theme.brockmann.button.semibold.medium,
                color = Theme.v2.colors.text.primary,
                modifier =
                    Modifier.clip(PillShape)
                        .background(Theme.v2.colors.buttons.ctaPrimary)
                        .clickable(onClick = onClaim)
                        .padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun QbtcCoinDecorations() {
    Box(modifier = Modifier.fillMaxSize()) {
        QbtcCoin(
            width = 40.dp,
            height = 45.dp,
            rotation = -26.5f,
            alignment = Alignment.CenterStart,
            offsetX = 7.dp,
            offsetY = (-55).dp,
        )
        QbtcCoin(
            width = 90.dp,
            height = 100.dp,
            rotation = 13f,
            alignment = Alignment.CenterStart,
            offsetX = (-30).dp,
            offsetY = 11.dp,
        )
        QbtcCoin(
            width = 50.dp,
            height = 55.dp,
            rotation = 8.5f,
            alignment = Alignment.CenterStart,
            offsetX = 10.dp,
            offsetY = 72.dp,
        )
        QbtcCoin(
            width = 87.dp,
            height = 98.dp,
            rotation = 0f,
            alignment = Alignment.CenterEnd,
            offsetX = 17.dp,
            offsetY = (-50).dp,
        )
        QbtcCoin(
            width = 50.dp,
            height = 55.dp,
            rotation = -6.84f,
            alignment = Alignment.CenterEnd,
            offsetX = (-10).dp,
            offsetY = 45.dp,
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
                    .clip(PillShape)
                    .background(Theme.v2.colors.buttons.ctaPrimary)
                    .clickable(onClick = onClaim)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}

private val QbtcBannerGlow = Color(0xFF0538C7)
private val PillShape = RoundedCornerShape(99.dp)
