package com.vultisig.wallet.data.usecases

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.graphics.createBitmap
import androidx.core.graphics.scale

// `Bitmap.scale` delegates to `createScaledBitmap`, which rejects a source whose color space is
// null with "can't create bitmap without a color space" (some devices hand us such QR/logo
// bitmaps). Redraw those into a fresh sRGB-backed bitmap before scaling so callers can never crash
// on this input.
internal fun Bitmap.scaleWithColorSpace(width: Int, height: Int, filter: Boolean = true): Bitmap {
    val source =
        if (colorSpace == null) {
            createBitmap(this.width, this.height, config ?: Bitmap.Config.ARGB_8888).also {
                Canvas(it).drawBitmap(this, 0f, 0f, null)
            }
        } else {
            this
        }
    val scaled = source.scale(width, height, filter)
    if (source !== this) source.recycle()
    return scaled
}
