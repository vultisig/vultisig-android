package com.vultisig.wallet.presenter.common

import android.graphics.Bitmap
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.vultisig.wallet.ui.theme.Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
internal fun QRCodeKeyGenImage(
    qrCodeContent: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.drawBehind {
            drawRoundRect(
                color = Color("#33e6bf".toColorInt()), style = Stroke(
                    width = 8f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(50f, 50f), 0.0f)
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
): BitmapPainter {
    var bitmap by remember(qrCodeContent) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(bitmap) {
        if (bitmap != null) return@LaunchedEffect

        launch(Dispatchers.IO) {
            val qrCodeWriter = QRCodeWriter()
            val hintMap = mapOf(EncodeHintType.MARGIN to 0)
            bitmap = try {
                val bitmapMatrix = qrCodeWriter.encode(
                    qrCodeContent,
                    BarcodeFormat.QR_CODE,
                    0,
                    0,
                    hintMap
                )

                val matrixWidth = bitmapMatrix.width
                val matrixHeight = bitmapMatrix.height

                val newBitmap = Bitmap.createBitmap(
                    matrixWidth,
                    matrixHeight,
                    Bitmap.Config.ARGB_8888,
                )

                for (x in 0 until matrixWidth) {
                    for (y in 0 until matrixHeight) {
                        val shouldColorPixel = bitmapMatrix?.get(x, y) ?: false
                        val pixelColor =
                            if (shouldColorPixel) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

                        newBitmap.setPixel(x, y, pixelColor)
                    }
                }

                newBitmap
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

@Preview
@Composable
private fun QRCodeKeyGenImagePreview() {
    QRCodeKeyGenImage(qrCodeContent = "")
}