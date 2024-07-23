package com.vultisig.wallet.presenter.common

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import androidx.core.graphics.toColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private const val QR_CODE_SCALE_FACTOR = 8
private const val QR_CODE_VS_LOGO_SCALE_FACTOR = 4

@Composable
internal fun QRCodeKeyGenImage(
    qrCodeContent: String,
    modifier: Modifier = Modifier,
    pathEffect: PathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 50f), 0.0f),
) {
    Box(
        modifier = modifier.drawBehind {
            drawRoundRect(
                color = Color("#33e6bf".toColorInt()), style = Stroke(
                    width = 8f,
                    pathEffect = pathEffect,
                ), cornerRadius = CornerRadius(16.dp.toPx())
            )
        }
    ) {
        Surface(
            color = Theme.colors.neutral0,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .padding(16.dp),
        ) {
            Image(
                painter = rememberQRBitmapPainter(
                    qrCodeContent = qrCodeContent,
                ),
                contentDescription = "qr",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
internal fun rememberQRBitmapPainter(
    qrCodeContent: String,
    mainColor: Color = Theme.colors.neutral900,
    backgroundColor: Color = Theme.colors.neutral0,
    logo : Bitmap? = null
): BitmapPainter {
    var bitmap by remember(qrCodeContent) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(bitmap) {
        if (bitmap != null) return@LaunchedEffect

        launch(Dispatchers.IO) {
            bitmap = try {
                generateQrBitmap(qrCodeContent, mainColor, backgroundColor, logo)
            } catch (ex: WriterException) {
                null
            }
        }
    }

    return remember(bitmap) {
        val currentBitmap = bitmap ?: Bitmap.createBitmap(
            1,
            1,
            Bitmap.Config.ARGB_8888,
        ).apply {
            eraseColor(0x00000000)
        }

        BitmapPainter(
            currentBitmap.asImageBitmap(),
            filterQuality = FilterQuality.None
        )
    }
}
 internal fun generateQrBitmap(
    qrCodeContent: String,
    mainColor: Color = Color.Black,
    backgroundColor: Color = Color.White,
    logo : Bitmap? = null,
): Bitmap {
    val hintMap = mapOf(EncodeHintType.MARGIN to 0)

    val qrCodeWriter = QRCodeWriter()
    val bitmapMatrix = qrCodeWriter.encode(
        qrCodeContent,
        BarcodeFormat.QR_CODE,
        0,
        0,
        hintMap
    )

    val matrixWidth = bitmapMatrix.width
    val matrixHeight = bitmapMatrix.height

    val bitmap = Bitmap.createBitmap(
        matrixWidth,
        matrixHeight,
        Bitmap.Config.ARGB_8888,
    )

    for (x in 0 until matrixWidth) {
        for (y in 0 until matrixHeight) {
            val shouldColorPixel = bitmapMatrix?.get(x, y) ?: false
            val pixelColor =
                if (shouldColorPixel) mainColor.toArgb() else backgroundColor.toArgb()

            bitmap.setPixel(x, y, pixelColor)
        }
    }
    if (logo == null) return bitmap

    val scaledWidth = bitmap.width * QR_CODE_SCALE_FACTOR
    val scaledHeight = bitmap.height * QR_CODE_SCALE_FACTOR
    val scaledLogoWidth = scaledWidth / QR_CODE_VS_LOGO_SCALE_FACTOR
    val scaledLogoHeight = scaledHeight / QR_CODE_VS_LOGO_SCALE_FACTOR
    val scaledLogo = logo.scale(scaledLogoWidth, scaledLogoHeight)
    val scaledBitmap = bitmap.scale(scaledWidth, scaledHeight, false)

    val canvas = Canvas(scaledBitmap)

    val xLogo = (scaledBitmap.width - scaledLogo.width) / 2f
    val yLogo = (scaledBitmap.height - scaledLogo.height) / 2f

    canvas.drawBitmap(scaledLogo, xLogo, yLogo, null)
     bitmap.recycle()
     scaledLogo.recycle()

    return scaledBitmap
}

@Preview
@Composable
private fun QRCodeKeyGenImagePreview() {
    QRCodeKeyGenImage(qrCodeContent = "")
}