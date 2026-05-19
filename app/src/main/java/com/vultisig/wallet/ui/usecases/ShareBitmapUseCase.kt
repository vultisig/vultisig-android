package com.vultisig.wallet.ui.usecases

import android.content.Context
import android.graphics.Bitmap
import com.vultisig.wallet.ui.utils.share
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun interface ShareBitmapUseCase {
    suspend operator fun invoke(bitmap: Bitmap, fileName: String)
}

internal class ShareBitmapUseCaseImpl
@Inject
constructor(@param:ApplicationContext private val context: Context) : ShareBitmapUseCase {
    override suspend fun invoke(bitmap: Bitmap, fileName: String) =
        withContext(Dispatchers.IO) { context.share(bitmap, fileName) }
}
