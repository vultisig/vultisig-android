package com.vultisig.wallet.data.usecases

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.util.TypedValue
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.createBitmap
import com.vultisig.wallet.ui.theme.v2.V2
import com.vultisig.wallet.ui.theme.v2.V2.colors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class QrBitmapData(val bitmap: Bitmap?, val bitmapPainter: BitmapPainter?)

internal interface GenerateAccountQrUseCase : suspend (String, Int?) -> QrBitmapData

internal class GenerateAccountQrUseCaseImpl
@Inject
constructor(
    private val generateQrBitmap: GenerateQrBitmap,
    @param:ApplicationContext private val context: Context,
) : GenerateAccountQrUseCase {
    override suspend fun invoke(address: String, logo: Int?): QrBitmapData {

        val bitmap =
            logo
                ?.let { AppCompatResources.getDrawable(context, logo) }
                ?.let { drawable ->
                    val desiredSize =
                        TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP,
                                LOGO_SIZE_DP.toFloat(),
                                context.resources.displayMetrics,
                            )
                            .toInt()
                    val bitmap = createBitmap(desiredSize, desiredSize)
                    val canvas = Canvas(bitmap)
                    val path = Path()
                    val radius = minOf(canvas.width, canvas.height) / LOGO_RADIUS_DIVISOR

                    path.addCircle(
                        canvas.width / 2f,
                        canvas.height / 2f,
                        radius,
                        Path.Direction.CCW,
                    )
                    canvas.clipPath(path)
                    canvas.drawColor(V2.colors.neutrals.n50.toArgb())
                    drawable.setBounds(0, 0, canvas.width, canvas.height)
                    drawable.draw(canvas)
                    bitmap
                }

        return generateQr(address, bitmap)
    }

    private suspend fun generateQr(address: String, logo: Bitmap?): QrBitmapData {
        val qrBitmap =
            // Dark modules on a light, opaque background — not just app-internal contrast.
            // Third-party scanners (notably iOS's Camera/Vision QR reader) frequently fail to
            // decode color-inverted (light-on-dark) QR codes that our own in-app scanner tolerates.
            withContext(Dispatchers.IO) {
                generateQrBitmap(address, colors.neutrals.n900, colors.neutrals.n50, logo)
            }

        val bitmapPainter =
            BitmapPainter(image = qrBitmap.asImageBitmap(), filterQuality = FilterQuality.None)
        return QrBitmapData(bitmap = qrBitmap, bitmapPainter = bitmapPainter)
    }

    companion object {
        private const val LOGO_RADIUS_DIVISOR = 2.3f
        private const val LOGO_SIZE_DP = 32
    }
}
