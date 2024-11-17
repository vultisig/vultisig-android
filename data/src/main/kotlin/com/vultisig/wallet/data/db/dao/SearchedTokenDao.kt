package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.vultisig.wallet.data.db.models.SearchedTokenEntity
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.VaultId

@Dao
interface SearchedTokenDao {

    @Query("SELECT * FROM searchedToken WHERE vaultId = :vaultId AND chain = :chainId")
    suspend fun getSearchedTokens(vaultId: VaultId, chainId: ChainId): List<SearchedTokenEntity>

    @Upsert
    suspend fun upsert(entry: SearchedTokenEntity)

}