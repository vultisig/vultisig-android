package com.vultisig.wallet.data.usecases.file

import android.content.Context
import android.net.Uri
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.data.common.fileContent
import com.vultisig.wallet.data.common.fileName
import com.vultisig.wallet.data.common.isValidZipFile
import com.vultisig.wallet.data.common.processZip
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

interface VaultFileReaderUseCase {
    suspend fun readContent(uri: Uri): String?

    suspend fun readName(uri: Uri): String?

    suspend fun isValidZip(uri: Uri): Boolean

    suspend fun extractZipEntries(uri: Uri): List<AppZipEntry>
}

internal class VaultFileReaderUseCaseImpl
@Inject
constructor(@param:ApplicationContext private val context: Context) : VaultFileReaderUseCase {
    override suspend fun readContent(uri: Uri): String? = uri.fileContent(context)

    override suspend fun readName(uri: Uri): String? = uri.fileName(context)

    override suspend fun isValidZip(uri: Uri): Boolean = uri.isValidZipFile(context)

    override suspend fun extractZipEntries(uri: Uri): List<AppZipEntry> = uri.processZip(context)
}
