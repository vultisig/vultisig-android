package com.vultisig.wallet.presenter.common

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun QRCodeKeyGenImage(
    qrCodeContent: String,
    imageWidthInDp: Dp = 250.dp,
    imageHeightInDp: Dp = 250.dp,
) {

    Image(
        painter = rememberQRBitmapPainter(
            qrCodeContent = qrCodeContent,
            imageWidthInDp = imageWidthInDp,
            imageHeightInDp = imageHeightInDp
        ),
        contentScale = ContentScale.FillBounds,
        contentDescription = "devices",
        modifier = Modifier
            .width(imageWidthInDp)
            .height(imageHeightInDp)

            .drawBehind {
                drawRoundRect(
                    color = Color("#33e6bf".toColorInt()), style = Stroke(
                        width = 8f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 50f), 0.0f)
                    ), cornerRadius = CornerRadius(16.dp.toPx())
                )
            }
            .padding(20.dp)
    )
}

@Composable
fun rememberQRBitmapPainter(
    qrCodeContent: String,
    imageWidthInDp: Dp,
    imageHeightInDp: Dp,
): BitmapPainter {
    val density = LocalDensity.current
    val imageWidthPx = with(density) { imageWidthInDp.roundToPx() }
    val imageHeightPx = with(density) { imageHeightInDp.roundToPx() }

    var bitmap by remember(qrCodeContent) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(bitmap) {
        if (bitmap != null) return@LaunchedEffect

        launch(Dispatchers.IO) {
            val qrCodeWriter = QRCodeWriter()

            val bitmapMatrix = try {
                qrCodeWriter.encode(
                    qrCodeContent,
                    BarcodeFormat.QR_CODE,
                    imageWidthPx,
                    imageHeightPx
                )
            } catch (ex: WriterException) {
                null
            }

            val matrixWidth = bitmapMatrix?.width ?: imageWidthPx
            val matrixHeight = bitmapMatrix?.height ?: imageHeightPx

            val newBitmap = Bitmap.createBitmap(
                matrixWidth,
                matrixHeight,
                Bitmap.Config.ARGB_8888
            )

            for (x in 0 until matrixWidth) {
                for (y in 0 until matrixHeight) {
                    val shouldColorPixel = bitmapMatrix?.get(x, y) ?: false
                    val pixelColor =
                        if (shouldColorPixel) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

                    newBitmap.setPixel(x, y, pixelColor)
                }
            }
            bitmap = newBitmap
        }
    }

    return remember(bitmap) {
        val currentBitmap = bitmap ?: Bitmap.createBitmap(
            imageWidthPx,
            imageHeightPx,
            Bitmap.Config.ARGB_8888,
        ).apply {
            eraseColor(0x00000000)
        }

        BitmapPainter(currentBitmap.asImageBitmap())
    }
}

@Preview
@Composable
fun QRCodeKeyGenImagePreview() {
    QRCodeKeyGenImage(qrCodeContent = "")
}