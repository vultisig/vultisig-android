package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.TokenPriceEntity

@Dao
interface TokenPriceDao {

    @Query(
        "SELECT price FROM tokenPrice WHERE " +
                "tokenId COLLATE NOCASE = :tokenId AND currency = :currency"
    )
    suspend fun getTokenPrice(
        tokenId: String,
        currency: String
    ): String?

    @Query(
        "SELECT * FROM tokenPrice WHERE " +
                "tokenId IN (:tokenIds) AND currency = :currency"
    )
    suspend fun getTokenPrices(
        tokenIds: List<String>,
        currency: String
    ): List<TokenPriceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokenPrice(tokenPrice: TokenPriceEntity)

}