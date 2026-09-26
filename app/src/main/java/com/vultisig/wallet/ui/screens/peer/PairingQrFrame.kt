package com.vultisig.wallet.ui.screens.peer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.vultisig.wallet.ui.theme.Theme

// Gradient border that wraps the QR card (Figma: top #4879FD -> bottom #0D39B1). Not a theme token
// because this exact pairing is unique to the pairing QR frame.
private val QrFrameGradient =
    Brush.verticalGradient(colors = listOf(Color(0xFF4879FD), Color(0xFF0D39B1)))

/**
 * The gradient-framed QR card shown on the pairing screen and on its share sheet.
 *
 * Fills the width it is given; the QR inside stays square.
 *
 * @param qrCode the QR painter, or null while it is still being generated.
 * @param onClick invoked on a tap anywhere on the card, or null for a card that is not tappable.
 */
@Composable
internal fun PairingQrFrame(
    qrCode: BitmapPainter?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    // Off the scale by construction, not by drift: these two trace a frame drawn around the QR
    // bitmap, so the inner corner has to sit a fixed inset inside the outer one. Rounding either to
    // a step would break the concentricity the frame depends on.
    val outerShape = RoundedCornerShape(24.75.dp)
    val innerShape = RoundedCornerShape(18.56.dp)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(outerShape)
                .background(brush = QrFrameGradient, shape = outerShape)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(6.19.dp)
    ) {
        Box(
            modifier =
                Modifier.fillMaxWidth()
                    .background(color = Theme.v2.colors.backgrounds.surface1, shape = innerShape)
                    .border(
                        width = 0.77.dp,
                        color = Theme.v2.colors.border.normal,
                        shape = innerShape,
                    )
                    .padding(12.38.dp)
        ) {
            // Reserve the QR footprint so the framed card keeps its size while the bitmap is still
            // being generated, then fade the QR in once it is ready.
            QrCodeImage(qrCode = qrCode)
        }
    }
}

/**
 * Reserves the QR footprint and fades the generated QR bitmap in once it is ready.
 *
 * Kept as a standalone composable so [AnimatedVisibility] resolves to the non-scoped overload
 * instead of an enclosing [androidx.compose.foundation.layout.ColumnScope] extension.
 *
 * @param qrCode the QR painter, or null while it is still being generated.
 */
@Composable
private fun QrCodeImage(qrCode: BitmapPainter?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(1f), contentAlignment = Alignment.Center) {
        AnimatedVisibility(visible = qrCode != null, enter = fadeIn()) {
            if (qrCode != null) {
                Image(
                    painter = qrCode,
                    contentDescription = null,
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
