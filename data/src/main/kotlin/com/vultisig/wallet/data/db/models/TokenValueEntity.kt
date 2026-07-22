package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.vultisig.wallet.data.models.Chain

// contractAddress is part of the primary key (MIGRATION_34_35) so two THORChain secured assets
// sharing a ticker on different underlying chains (e.g. ETH.USDC and AVAX.USDC, same vault
// address) get distinct cache rows instead of colliding — see Coin.id for the identical need.
@Entity(tableName = "tokenValue", primaryKeys = ["chain", "address", "ticker", "contractAddress"])
data class TokenValueEntity(
    @ColumnInfo("chain") val chain: String,
    @ColumnInfo("address") val address: String,
    @ColumnInfo("ticker") val ticker: String,
    @ColumnInfo("tokenValue") val tokenValue: String,
    @ColumnInfo("contractAddress") val contractAddress: String,
) {
    /**
     * Mirrors [com.vultisig.wallet.data.models.Coin.id] so lookups keyed on Coin.id resolve
     * correctly.
     */
    val tokenId: String
        get() = if (isSecuredAssetLike()) "$ticker-$chain-$contractAddress" else "$ticker-$chain"

    private fun isSecuredAssetLike(): Boolean {
        if (chain != Chain.ThorChain.id) return false
        if (contractAddress.startsWith("x/", ignoreCase = true)) return false
        val parts = contractAddress.split("-", limit = 2)
        return parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
    }
}
