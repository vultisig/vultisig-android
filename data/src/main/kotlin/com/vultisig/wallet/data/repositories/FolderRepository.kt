package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.FolderDao
import com.vultisig.wallet.data.db.models.FolderEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface FolderRepository {
    suspend fun insertFolder(name: String): Int
    fun getAll(): Flow<List<FolderEntity>>
}

internal class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
): FolderRepository {
    override suspend fun insertFolder(name: String): Int =
        folderDao.insertFolder(FolderEntity(name = name)).toInt()

    override fun getAll(): Flow<List<FolderEntity>> {
        return folderDao.getAll()
    }
}