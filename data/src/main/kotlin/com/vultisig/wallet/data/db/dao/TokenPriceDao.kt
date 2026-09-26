package com.vultisig.wallet.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vultisig.wallet.data.db.models.TokenPriceEntity

/** Exact id wins. A case-variant row (Cake-BSC vs CAKE-BSC) used to hide every newer price. */
internal const val TOKEN_PRICE_BY_ID =
    "SELECT price FROM tokenPrice WHERE " +
        "tokenId COLLATE NOCASE = :tokenId AND currency = :currency " +
        "ORDER BY CASE WHEN tokenId = :tokenId THEN 0 ELSE 1 END LIMIT 1"

@Dao
interface TokenPriceDao {

    @Query(TOKEN_PRICE_BY_ID)
    suspend fun getTokenPrice(tokenId: String, currency: String): String?

    @Query("SELECT * FROM tokenPrice WHERE " + "tokenId IN (:tokenIds) AND currency = :currency")
    suspend fun getTokenPrices(tokenIds: List<String>, currency: String): List<TokenPriceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTokenPrice(tokenPrice: TokenPriceEntity)
}
