package com.vultisig.wallet.data.usecases.backup

import android.content.Context
import android.net.Uri
import com.vultisig.wallet.data.common.fileName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

val FILE_ALLOWED_EXTENSIONS = listOf("bak", "dat", "vult")

fun interface IsVaultBackupFileExtensionValidUseCase {
    operator fun invoke(uri: Uri): Boolean
}

class IsVaultBackupFileExtensionValidUseCaseImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : IsVaultBackupFileExtensionValidUseCase {

    override fun invoke(uri: Uri): Boolean = FILE_ALLOWED_EXTENSIONS.any {
        it == File(uri.fileName(context)).extension
    }

}