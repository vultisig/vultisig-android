package com.vultisig.wallet.ui.screens.qbtc

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
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
                .border(
                    width = 1.dp,
                    color = Theme.v2.colors.border.light,
                    shape = RoundedCornerShape(12.dp),
                ),
    ) {
        QbtcCoinDecorations()

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.qbtc_claim_banner_subtitle),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.tertiary,
                textAlign = TextAlign.Center,
            )
            UiSpacer(size = 8.dp)
            Text(
                text = stringResource(R.string.qbtc_claim_banner_title),
                style = Theme.brockmann.headings.title2,
                color = Theme.v2.colors.text.primary,
                textAlign = TextAlign.Center,
            )
            UiSpacer(size = 16.dp)
            Text(
                text = stringResource(R.string.qbtc_claim_banner_cta),
                style = Theme.brockmann.supplementary.caption,
                color = Theme.v2.colors.text.primary,
                modifier =
                    Modifier.clip(RoundedCornerShape(30.dp))
                        .background(Theme.v2.colors.buttons.ctaPrimary)
                        .clickable(onClick = onClaim)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun QbtcCoinDecorations() {
    Box(modifier = Modifier.fillMaxWidth().height(156.dp)) {
        QbtcCoin(
            size = 90.dp,
            rotation = 13.22f,
            modifier = Modifier.align(Alignment.CenterStart).offset(x = (-38).dp),
        )
        QbtcCoin(
            size = 43.dp,
            rotation = -26.62f,
            modifier = Modifier.align(Alignment.TopStart).offset(x = (-2).dp, y = (-1).dp),
        )
        QbtcCoin(
            size = 44.dp,
            rotation = 8.42f,
            modifier = Modifier.align(Alignment.BottomStart).offset(x = (-2).dp, y = (-1).dp),
        )
        QbtcCoin(
            size = 80.dp,
            rotation = 0f,
            modifier = Modifier.align(Alignment.TopEnd).offset(x = 24.dp, y = (-23).dp),
        )
        QbtcCoin(
            size = 43.dp,
            rotation = -6.84f,
            modifier = Modifier.align(Alignment.BottomEnd).offset(x = 16.dp, y = (-12).dp),
        )
    }
}

@Composable
private fun QbtcCoin(size: androidx.compose.ui.unit.Dp, rotation: Float, modifier: Modifier) {
    Image(
        painter = painterResource(R.drawable.qbtc),
        contentDescription = null,
        modifier = modifier.size(size).rotate(rotation),
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
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors =
                                listOf(
                                    androidx.compose.ui.graphics.Color.Transparent,
                                    Theme.v2.colors.backgrounds.background,
                                )
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
                    .clip(RoundedCornerShape(99.dp))
                    .background(Theme.v2.colors.buttons.ctaPrimary)
                    .clickable(onClick = onClaim)
                    .padding(horizontal = 24.dp, vertical = 12.dp),
        )
    }
}
