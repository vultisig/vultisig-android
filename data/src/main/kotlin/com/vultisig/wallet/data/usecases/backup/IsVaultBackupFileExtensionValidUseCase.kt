package com.vultisig.wallet.data.usecases.backup

import android.content.Context
import android.net.Uri
import com.vultisig.wallet.data.common.fileName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

val FILE_ALLOWED_EXTENSIONS = listOf(
    "bak",
    "dat",
    "vult",
    "txt"
)

fun interface IsVaultBackupFileExtensionValidUseCase {
    suspend operator fun invoke(uri: Uri, mimeType: MimeType): Boolean
}

class IsVaultBackupFileExtensionValidUseCaseImpl @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : IsVaultBackupFileExtensionValidUseCase {

    override suspend operator fun invoke(uri: Uri, mimeType: MimeType): Boolean {
        val file = File(uri.fileName(context) ?: return false)
        return when (mimeType) {
            MimeType.OCTET_STREAM -> {
                FILE_ALLOWED_EXTENSIONS.any {
                    it == file.extension
                }
            }

            MimeType.ZIP -> file.extension.equals(
                "zip",
                ignoreCase = true
            )
        }
    }

}