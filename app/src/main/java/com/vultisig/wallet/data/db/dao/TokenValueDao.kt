package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.TokenValueEntity

@Dao
internal interface TokenValueDao {

    @Query(
        "SELECT tokenValue FROM tokenValue WHERE " +
                "chain = :chainId AND address = :address AND ticker = :ticker"
    )
    suspend fun getTokenValue(
        chainId: String,
        address: String,
        ticker: String
    ): String?

    @Query(
        "SELECT * FROM tokenValue WHERE address IN (:addresses)"
    )
    suspend fun getTokenValues(
        addresses: List<String>,
    ): List<TokenValueEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokenValue(tokenValue: TokenValueEntity)

}