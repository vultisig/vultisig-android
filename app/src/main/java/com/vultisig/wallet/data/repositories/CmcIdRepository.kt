package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.CmcPriceDao
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

internal interface CmcIdRepository {

    suspend fun getCmcId(coin: Coin): CoinCmcPrice

    suspend fun getCmcIds(coins: List<Coin>): List<CoinCmcPrice>

}

internal class CmcIdRepositoryImpl @Inject constructor(
    private val cmcPriceDao: CmcPriceDao
) : CmcIdRepository {

    override suspend fun getCmcId(coin: Coin) =
        CoinCmcPrice(
            tokenId = coin.id,
            cmcId = cmcPriceDao.getCmcId(coin.contractAddress),
            chain = coin.chain,
            contractAddress = coin.contractAddress,
        )

    override suspend fun getCmcIds(coins: List<Coin>) = coroutineScope {
        coins.map {
            async {
                CoinCmcPrice(
                    tokenId = it.id,
                    cmcId = cmcPriceDao.getCmcId(it.contractAddress),
                    chain = it.chain,
                    contractAddress = it.contractAddress,
                )
            }
        }.awaitAll()
    }
}

internal data class CoinCmcPrice(
    val tokenId: String,
    val cmcId: Int?,
    val chain: Chain,
    val contractAddress: String,
) {
    val isNativeToken = contractAddress.isBlank()
}