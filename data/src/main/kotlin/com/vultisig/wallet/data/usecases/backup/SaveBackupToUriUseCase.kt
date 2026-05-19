package com.vultisig.wallet.data.usecases.backup

import android.content.Context
import android.net.Uri
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.data.common.saveContentToUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface SaveBackupToUriUseCase {
    suspend operator fun invoke(uri: Uri, content: String): Boolean

    suspend operator fun invoke(uri: Uri, content: List<AppZipEntry>): Boolean
}

internal class SaveBackupToUriUseCaseImpl
@Inject
constructor(@param:ApplicationContext private val context: Context) : SaveBackupToUriUseCase {
    override suspend fun invoke(uri: Uri, content: String): Boolean =
        context.saveContentToUri(uri, content)

    override suspend fun invoke(uri: Uri, content: List<AppZipEntry>): Boolean =
        context.saveContentToUri(uri, content)
}
