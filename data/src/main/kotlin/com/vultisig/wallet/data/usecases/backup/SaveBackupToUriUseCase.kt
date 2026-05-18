package com.vultisig.wallet.data.usecases.backup

import android.content.Context
import android.net.Uri
import com.vultisig.wallet.data.common.deleteDocument
import com.vultisig.wallet.data.common.saveContentToUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface SaveBackupToUriUseCase {
    suspend operator fun invoke(uri: Uri, content: String): Boolean
}

internal class SaveBackupToUriUseCaseImpl
@Inject
constructor(@param:ApplicationContext private val context: Context) : SaveBackupToUriUseCase {
    override suspend fun invoke(uri: Uri, content: String): Boolean =
        context.saveContentToUri(uri, content)
}

fun interface DeleteBackupDocumentUseCase {
    suspend operator fun invoke(uri: Uri): Boolean
}

internal class DeleteBackupDocumentUseCaseImpl
@Inject
constructor(@param:ApplicationContext private val context: Context) : DeleteBackupDocumentUseCase {
    override suspend fun invoke(uri: Uri): Boolean = context.deleteDocument(uri)
}
