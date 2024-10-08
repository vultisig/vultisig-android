package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.FolderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM vaultFolder WHERE id = :id")
    suspend fun getFolder(id: String): FolderEntity

    @Query("DELETE FROM vaultFolder WHERE id = :id")
    suspend fun deleteFolder(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFolder(folder: FolderEntity) : Long

    @Query("SELECT * FROM vaultFolder")
    fun getAll(): Flow<List<FolderEntity>>
}