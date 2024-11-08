package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.FolderDao
import com.vultisig.wallet.data.db.models.FolderEntity
import com.vultisig.wallet.data.models.Folder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface FolderRepository {
    suspend fun getFolder(id: String): Folder
    suspend fun deleteFolder(id: String)
    suspend fun insertFolder(name: String): Int
    suspend fun updateFolderName(id: String, name: String)
    fun getAll(): Flow<List<Folder>>
}

internal class FolderRepositoryImpl @Inject constructor(
    private val folderDao: FolderDao
): FolderRepository {
    override suspend fun getFolder(id: String): Folder =
        folderDao.getFolder(id).toFolder()

    override suspend fun deleteFolder(id: String) {
        folderDao.deleteFolder(id)
    }

    override suspend fun updateFolderName(id: String, name: String) {
        folderDao.updateFolderName(id, name)
    }

    override suspend fun insertFolder(name: String): Int =
        folderDao.insertFolder(FolderEntity(name = name)).toInt()

    override fun getAll(): Flow<List<Folder>> {
        return folderDao.getAll().map { it.map { folderEntity -> folderEntity.toFolder() } }
    }

    private fun FolderEntity.toFolder() = Folder(
        id = id,
        name = name,
    )
}