package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.SearchedTokenDao
import com.vultisig.wallet.data.db.models.SearchedTokenEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.VaultId
import javax.inject.Inject

interface SearchedTokensRepository {
    suspend fun getSearchedTokens(vaultId: VaultId, chainId: ChainId): List<Coin>
    suspend fun saveSearchedToken(coin: Coin, vaultId: VaultId)
}

class SearchedTokensRepositoryImpl @Inject constructor(
    private val searchedTokenDao: SearchedTokenDao,
) : SearchedTokensRepository {

    override suspend fun getSearchedTokens(vaultId: VaultId, chainId: ChainId): List<Coin> =
        searchedTokenDao.getSearchedTokens(vaultId, chainId).map { it.toCoin() }

    override suspend fun saveSearchedToken(coin: Coin, vaultId: VaultId) {
        searchedTokenDao.upsert(coin.toSearchedTokenEntity(vaultId))
    }

    private fun SearchedTokenEntity.toCoin() = Coin(
        chain = Chain.fromRaw(chain),
        ticker = ticker,
        logo = logo,
        address = "",
        decimal = decimals,
        hexPublicKey = "",
        priceProviderID = priceProviderID,
        contractAddress = contractAddress,
        isNativeToken = false,
    )

    private fun Coin.toSearchedTokenEntity(vaultId: VaultId) = SearchedTokenEntity(
        id = "${this.ticker}-${this.chain.raw}",
        chain = chain.id,
        ticker = ticker,
        logo = logo,
        decimals = decimal,
        priceProviderID = priceProviderID,
        contractAddress = contractAddress,
        vaultId = vaultId,
    )

}