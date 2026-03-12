package com.vultisig.wallet.ui.components.banners

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme

private const val BANNER_DISMISS_DURATION_MS = 30_000

private val bannerGradient
    @Composable
    get() =
        Brush.linearGradient(
            colors = listOf(Color(0xFF1547E8), Color(0xFF042B99)),
            start = Offset(0f, 0f),
            end = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
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
    val progress = remember(qrCodeData) { Animatable(1f) }

    LaunchedEffect(qrCodeData) {
        progress.animateTo(
            targetValue = 0f,
            animationSpec =
                tween(durationMillis = BANNER_DISMISS_DURATION_MS, easing = LinearEasing),
        )
        onDismiss()
    }

    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(shape)
                .background(bannerGradient)
                .clickable(onClick = onTap)
                .padding(horizontal = 20.dp, vertical = 20.dp),
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
            Icon(
                painter = painterResource(R.drawable.x),
                contentDescription = "Dismiss notification",
                tint = Theme.v2.colors.text.primary,
                modifier =
                    Modifier.size(32.dp)
                        .align(Alignment.CenterEnd)
                        .background(color = Color(0x33FFFFFF), shape = CircleShape)
                        .padding(8.dp)
                        .clickable(onClick = onDismiss),
            )
        }

        UiSpacer(size = 12.dp)

        if (vaultName.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier =
                    Modifier.background(
                            color = Color(0x33000000),
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

        UiSpacer(size = 16.dp)

        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color.White.copy(alpha = 0.2f))
        ) {
            Box(
                modifier =
                    Modifier.fillMaxHeight()
                        .fillMaxWidth(progress.value)
                        .background(Color.White.copy(alpha = 0.9f))
            )
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
