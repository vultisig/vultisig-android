package com.vultisig.wallet.data.usecases

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.toArgb
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.v2.V2.colors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface CreateQrCodeSharingBitmapUseCase {
    operator fun invoke(qr: Bitmap, info: QrShareInfo): Bitmap
}

internal class CreateQrCodeSharingBitmapUseCaseImpl
@Inject
constructor(
    @param:ApplicationContext private val context: Context,
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat,
) : CreateQrCodeSharingBitmapUseCase {
    override fun invoke(qr: Bitmap, info: QrShareInfo): Bitmap {
        val logo = BitmapFactory.decodeResource(context.resources, R.drawable.logo)

        return makeQrCodeBitmapShareFormat(
            context,
            qr,
            colors.backgrounds.primary.toArgb(),
            logo,
            info,
        )
    }
}
