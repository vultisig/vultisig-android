package com.vultisig.wallet.ui.screens.qbtc

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import app.rive.runtime.kotlin.core.Fit
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.rive.RiveAnimation
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.models.qbtc.QuantumSecurityIntroViewModel
import com.vultisig.wallet.ui.theme.Theme

private val HeroHeight = 240.dp

@Composable
internal fun QuantumSecurityIntroScreen(model: QuantumSecurityIntroViewModel = hiltViewModel()) {
    QuantumSecurityIntroScreenContent(onBack = model::back, onGetStarted = model::getStarted)
}

@Composable
internal fun QuantumSecurityIntroScreenContent(
    onBack: () -> Unit = {},
    onGetStarted: () -> Unit = {},
) {
    V3Scaffold(
        onBackClick = onBack,
        bottomBar = {
            VsButton(
                label = stringResource(R.string.qbtc_intro_get_started),
                variant = VsButtonVariant.CTA,
                onClick = onGetStarted,
                modifier =
                    Modifier.fillMaxWidth()
                        .padding(
                            horizontal = V3Scaffold.PADDING_HORIZONTAL,
                            vertical = V3Scaffold.PADDING_VERTICAL,
                        ),
            )
        },
        content = {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    text = stringResource(R.string.qbtc_intro_title),
                    style = Theme.brockmann.headings.title2,
                    color = Theme.v2.colors.text.primary,
                )

                UiSpacer(14.dp)

                Text(
                    text = stringResource(R.string.qbtc_intro_subtitle),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.secondary,
                )

                UiSpacer(24.dp)

                RiveAnimation(
                    animation = R.raw.riv_quantum_key_pair,
                    fit = Fit.CONTAIN,
                    modifier =
                        Modifier.fillMaxWidth()
                            .height(HeroHeight)
                            .dashedBorder(
                                color = Theme.v2.colors.border.light,
                                strokeWidth = 1.dp,
                                dashLength = 6.dp,
                                gapLength = 4.dp,
                            ),
                )

                UiSpacer(32.dp)

                FeatureRow(
                    icon = R.drawable.ic_shield,
                    title = stringResource(R.string.qbtc_intro_generate_title),
                    description = stringResource(R.string.qbtc_intro_generate_desc),
                )

                UiSpacer(16.dp)

                FeatureRow(
                    icon = R.drawable.ic_link,
                    title = stringResource(R.string.qbtc_intro_link_title),
                    description = stringResource(R.string.qbtc_intro_link_desc),
                )

                UiSpacer(16.dp)

                FeatureRow(
                    icon = R.drawable.ic_coins,
                    title = stringResource(R.string.qbtc_intro_claim_title),
                    description = stringResource(R.string.qbtc_intro_claim_desc),
                )

                UiSpacer(16.dp)
            }
        },
    )
}

@Composable
private fun FeatureRow(@DrawableRes icon: Int, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Image(
            painter = painterResource(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Theme.v2.colors.primary.accent4),
            modifier = Modifier.size(20.dp),
        )
        UiSpacer(16.dp)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Theme.brockmann.headings.subtitle,
                color = Theme.v2.colors.text.primary,
            )
            UiSpacer(8.dp)
            Text(
                text = description,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.v2.colors.text.tertiary,
            )
        }
    }
}

private fun Modifier.dashedBorder(
    color: Color,
    strokeWidth: Dp,
    dashLength: Dp,
    gapLength: Dp,
): Modifier = drawBehind {
    val stroke =
        Stroke(
            width = strokeWidth.toPx(),
            pathEffect =
                PathEffect.dashPathEffect(floatArrayOf(dashLength.toPx(), gapLength.toPx()), 0f),
        )
    drawRect(color = color, style = stroke)
}

@Composable
@Preview(widthDp = 360, heightDp = 800)
private fun QuantumSecurityIntroScreenPreview() {
    QuantumSecurityIntroScreenContent()
}
