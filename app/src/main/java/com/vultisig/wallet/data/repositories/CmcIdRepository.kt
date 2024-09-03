package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.CmcIdDao
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.CoinCmcPrice
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject

internal interface CmcIdRepository {

    suspend fun getCmcId(coin: Coin): CoinCmcPrice

    suspend fun getCmcIds(coins: List<Coin>): List<CoinCmcPrice>

}

internal class CmcIdRepositoryImpl @Inject constructor(
    private val cmcIdDao: CmcIdDao
) : CmcIdRepository {

    override suspend fun getCmcId(coin: Coin) =
        CoinCmcPrice(
            tokenId = coin.id,
            cmcId = cmcIdDao.getCmcId(coin.contractAddress),
            chain = coin.chain,
            contractAddress = coin.contractAddress,
        )

    override suspend fun getCmcIds(coins: List<Coin>) = coroutineScope {
        coins.map {
            async {
                CoinCmcPrice(
                    tokenId = it.id,
                    cmcId = cmcIdDao.getCmcId(it.contractAddress),
                    chain = it.chain,
                    contractAddress = it.contractAddress,
                )
            }
        }.awaitAll()
    }
}
