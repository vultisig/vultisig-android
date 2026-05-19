package com.vultisig.wallet.data.usecases.backup

import android.content.Context
import android.net.Uri
import com.vultisig.wallet.data.common.deleteDocument
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

fun interface DeleteBackupDocumentUseCase {
    suspend operator fun invoke(uri: Uri): Boolean
}

internal class DeleteBackupDocumentUseCaseImpl
@Inject
constructor(@param:ApplicationContext private val context: Context) : DeleteBackupDocumentUseCase {
    override suspend fun invoke(uri: Uri): Boolean = context.deleteDocument(uri)
}
