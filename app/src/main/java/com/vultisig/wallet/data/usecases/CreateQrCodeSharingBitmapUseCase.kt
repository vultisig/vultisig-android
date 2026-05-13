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

    // R.drawable.logo never changes; decode once and reuse across shares.
    private val logo: Bitmap by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.logo)
    }

    override fun invoke(qr: Bitmap, info: QrShareInfo): Bitmap =
        makeQrCodeBitmapShareFormat(context, qr, colors.backgrounds.primary.toArgb(), logo, info)
}
