package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.FolderDao
import com.vultisig.wallet.data.db.models.FolderEntity
import javax.inject.Inject

interface FolderRepository {
    suspend fun insertFolder(name: String)
    suspend fun getAll(): List<FolderEntity>
}

internal class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
): FolderRepository {
    override suspend fun insertFolder(name: String) {
        folderDao.insertFolder(FolderEntity(name = name))
    }
    override suspend fun getAll(): List<FolderEntity> {
        return folderDao.getAll()
    }
}