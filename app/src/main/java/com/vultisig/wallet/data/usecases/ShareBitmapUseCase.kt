package com.vultisig.wallet.data.usecases

import android.content.Context
import android.graphics.Bitmap
import com.vultisig.wallet.ui.utils.share
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface ShareBitmapUseCase {
    operator fun invoke(bitmap: Bitmap, fileName: String)
}

internal class ShareBitmapUseCaseImpl
@Inject
constructor(@param:ApplicationContext private val context: Context) : ShareBitmapUseCase {
    override fun invoke(bitmap: Bitmap, fileName: String) = context.share(bitmap, fileName)
}
