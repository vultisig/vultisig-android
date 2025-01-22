package com.vultisig.wallet.data.usecases

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.toArgb
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.theme.Colors
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface CreateQrCodeSharingBitmapUseCase {
    operator fun invoke(
        qr: Bitmap,
        @StringRes title: Int,
        @StringRes description: Int?,
    ): Bitmap
}

internal class CreateQrCodeSharingBitmapUseCaseImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat,
) : CreateQrCodeSharingBitmapUseCase {
    override fun invoke(
        qr: Bitmap,
        title: Int,
        description: Int?
    ): Bitmap {
        val titleString = context.getString(title)
        val descriptionString = description?.let { context.getString(it) }

        val logo = BitmapFactory.decodeResource(
            context.resources, R.drawable.ic_share_qr_logo
        )

        return makeQrCodeBitmapShareFormat(
            qr,
            Colors.Default.oxfordBlue800.toArgb(),
            logo,
            titleString,
            descriptionString,
        )
    }
}