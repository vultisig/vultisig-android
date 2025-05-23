package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vultisig.wallet.data.db.models.VaultMetadataEntity
import com.vultisig.wallet.data.models.VaultId

@Dao
interface VaultMetadataDao {

    @Query("SELECT * FROM vaultMetadata WHERE vaultId = :vaultId")
    suspend fun getBy(vaultId: VaultId): VaultMetadataEntity?

    @Upsert
    suspend fun upsert(entity: VaultMetadataEntity)

}