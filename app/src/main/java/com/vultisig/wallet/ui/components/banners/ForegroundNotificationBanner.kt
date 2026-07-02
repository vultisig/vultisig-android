package com.vultisig.wallet.ui.components.banners

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiIcon
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.launch

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
    transactionSummary: UiText,
    isActive: Boolean,
    onTap: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val summary = transactionSummary.asString()
    val shape = RoundedCornerShape(bottomStart = 40.dp, bottomEnd = 40.dp)
    val imagePainter = painterResource(R.drawable.foreground_layer)

    // Swipe-to-dismiss on two axes: lets the user flick away a stale or unwanted signing request
    // (e.g. an old notification) without acting on it. A horizontal flick past ~35% of the banner
    // width, or an upward drag past ~25% of its height, settles it off-screen and reports the
    // dismissal; a shorter drag springs back. Downward drag is clamped to 0 (the banner sits at the
    // top edge, so pushing it further into the content is meaningless).
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Re-center only when a live request (re)enters. A new or re-broadcast push produces a fresh
    // state instance, so `isActive` flips true and resets any residual swipe offset. Crucially we
    // do NOT reset on dismissal: after a successful swipe the banner must stay off-screen while the
    // parent's vertical exit transition plays out, otherwise the snap-back rides that exit and the
    // banner visibly re-centers then slides up (#5001).
    LaunchedEffect(qrCodeData, isActive) {
        if (isActive) {
            offsetX.snapTo(0f)
            offsetY.snapTo(0f)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.value.roundToInt(), offsetY.value.roundToInt()) }
                .pointerInput(Unit) {
                    val horizontalThreshold = size.width * 0.35f
                    val verticalThreshold = size.height * 0.25f
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                offsetX.snapTo(offsetX.value + dragAmount.x)
                                // Only allow upward travel; a downward drag can't push the banner
                                // deeper into the content below it.
                                offsetY.snapTo((offsetY.value + dragAmount.y).coerceAtMost(0f))
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                when {
                                    -offsetY.value >= verticalThreshold -> {
                                        offsetY.animateTo(-size.height.toFloat())
                                        // Leave the banner off-screen and clear the request; the
                                        // retained offset keeps it hidden through the parent's
                                        // vertical exit transition. The LaunchedEffect above
                                        // re-centers it only when a fresh request next (re)enters.
                                        onDismiss()
                                    }
                                    abs(offsetX.value) >= horizontalThreshold -> {
                                        val target =
                                            if (offsetX.value > 0) size.width.toFloat()
                                            else -size.width.toFloat()
                                        offsetX.animateTo(target)
                                        onDismiss()
                                    }
                                    else -> {
                                        offsetX.animateTo(0f)
                                        offsetY.animateTo(0f)
                                    }
                                }
                            }
                        },
                        // A drag interrupted by a second pointer or an arena steal runs neither
                        // onDragEnd branch; without this the banner freezes at its partial offset.
                        onDragCancel = {
                            scope.launch {
                                offsetX.animateTo(0f)
                                offsetY.animateTo(0f)
                            }
                        },
                    )
                }
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

            if (summary.isNotEmpty()) {
                Text(
                    text = summary,
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
        transactionSummary = UiText.DynamicString("Swap 10 ETH → USDC"),
        isActive = true,
        onTap = {},
        onDismiss = {},
    )
}
