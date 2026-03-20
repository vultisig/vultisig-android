package com.vultisig.wallet.ui.components.banners

import androidx.compose.foundation.Image
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButton
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonSize
import com.vultisig.wallet.ui.components.v2.buttons.VsCircleButtonType
import com.vultisig.wallet.ui.theme.Theme

private val bannerGradient
    @Composable
    get() =
        Brush.linearGradient(
            colorStops =
                arrayOf(
                    0.2077f to Theme.v2.colors.primary.accent2,
                    1.0f to Theme.v2.colors.primary.accent4,
                ),
            start = Offset(0f, Float.POSITIVE_INFINITY),
            end = Offset(Float.POSITIVE_INFINITY, 0f),
        )

@Composable
internal fun ForegroundNotificationBanner(
    qrCodeData: String,
    vaultName: String,
    transactionSummary: String,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bannerGradient)
                .clickable(onClick = onTap)
    ) {
        Image(
            painter = painterResource(R.drawable.foreground_layer),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.foreground_notification_banner_title),
                    style = Theme.brockmann.headings.title3,
                    color = Theme.v2.colors.text.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center),
                )
                VsCircleButton(
                    onClick = onDismiss,
                    icon = R.drawable.x,
                    size = VsCircleButtonSize.Small,
                    type = VsCircleButtonType.Tertiary,
                    modifier = Modifier.align(Alignment.CenterEnd),
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
        onDismiss = {},
    )
}
