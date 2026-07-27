package com.vultisig.wallet.data.db.models

import androidx.room.ColumnInfo
import androidx.room.Entity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.parseRippleTokenIdentity

// contractAddress is part of the primary key (MIGRATION_34_35) so two assets sharing a ticker get
// distinct cache rows instead of colliding — two THORChain secured assets on different underlying
// chains (ETH.USDC and AVAX.USDC), or one XRPL currency held from two different issuers (a USD
// trust line minted by two accounts). See Coin.id for the identical need.
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
     * correctly. Both coin types Coin.id contract-qualifies must be qualified here too, or a cached
     * balance row collapses onto the plain `ticker-chain` id and no longer matches its coin (e.g.
     * `BalanceRepository.getCachedTokenBalances` resolving decimals/price by id).
     */
    val tokenId: String
        get() =
            if (isSecuredAssetLike() || isRippleIssuedTokenLike()) {
                "$ticker-$chain-$contractAddress"
            } else {
                "$ticker-$chain"
            }

    private fun isSecuredAssetLike(): Boolean {
        if (chain != Chain.ThorChain.id) return false
        if (contractAddress.startsWith("x/", ignoreCase = true)) return false
        val parts = contractAddress.split("-", limit = 2)
        return parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()
    }

    // Native XRP carries an empty contractAddress, which parses to null, so only issued currencies
    // ("<currency>.<issuer>") qualify — matching Coin.isRippleIssuedToken.
    private fun isRippleIssuedTokenLike(): Boolean =
        chain == Chain.Ripple.id && parseRippleTokenIdentity(contractAddress) != null
}
