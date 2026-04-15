package com.vultisig.wallet.ui.components.banners

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

// background: url(<image>) lightgray -122.071px -89.692px / 162.123% 190.598% no-repeat,
//             linear-gradient(46deg, #0439C7 20.77%, #4879FD 109.97%);
private fun Modifier.bannerBackground(
    imagePainter: Painter,
    gradientStart: Color,
    gradientEnd: Color,
): Modifier = drawWithCache {
    // Layer 2 (bottom): linear-gradient(46deg, #0439C7 20.77%, #4879FD 109.97%)
    val angleRad = Math.toRadians(46.0)
    val sinA = sin(angleRad).toFloat()
    val cosA = cos(angleRad).toFloat()
    val halfLen = (abs(size.width * sinA) + abs(size.height * cosA)) / 2f
    val cx = size.width / 2f
    val cy = size.height / 2f
    val brush =
        Brush.linearGradient(
            colorStops = arrayOf(0.2077f to gradientStart, 1.0997f to gradientEnd),
            start = Offset(cx - sinA * halfLen, cy + cosA * halfLen),
            end = Offset(cx + sinA * halfLen, cy - cosA * halfLen),
        )
    // Layer 1 (top): url(<image>) -122.071px -89.692px / 162.123% 190.598% no-repeat
    val imgWidth = size.width * 1.62123f
    val imgHeight = size.height * 1.90598f
    onDrawBehind {
        drawRect(brush = brush)
        translate(left = -122.071f * density, top = -89.692f * density) {
            with(imagePainter) { draw(Size(imgWidth, imgHeight)) }
        }
    }
}

@Composable
internal fun ForegroundNotificationBanner(
    qrCodeData: String,
    vaultName: String,
    transactionSummary: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
    val imagePainter = painterResource(R.drawable.foreground_layer)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .bannerBackground(
                    imagePainter = imagePainter,
                    gradientStart = Theme.v2.colors.primary.accent2,
                    gradientEnd = Theme.v2.colors.primary.accent4,
                )
                .clickable(onClick = onTap)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            UiSpacer(67.dp)
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.foreground_notification_banner_title),
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            UiSpacer(size = 12.dp)

            if (vaultName.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier =
                        Modifier.background(
                                color = Theme.v2.colors.backgrounds.disabled,
                                shape = RoundedCornerShape(99.dp),
                            )
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    UiIcon(
                        drawableResId = R.drawable.ic_shield,
                        size = 14.dp,
                        tint = Theme.v2.colors.alerts.success,
                    )
                    Text(
                        text = vaultName,
                        style = Theme.brockmann.body.s.medium,
                        color = Theme.v2.colors.text.primary,
                    )
                }

                UiSpacer(size = 8.dp)
            }

            if (transactionSummary.isNotEmpty()) {
                Text(
                    text = transactionSummary,
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.secondary,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    text = stringResource(R.string.keysign_notification_body),
                    style = Theme.brockmann.supplementary.footnote,
                    color = Theme.v2.colors.text.secondary,
                    textAlign = TextAlign.Center,
                )
            }
            UiSpacer(24.dp)
        }
    }
}

@Preview
@Composable
private fun ForegroundNotificationBannerPreview() {
    ForegroundNotificationBanner(
        qrCodeData = "preview",
        vaultName = "Vault #2",
        transactionSummary = "Swap 10 ETH → USDC",
        onTap = {},
    )
}
