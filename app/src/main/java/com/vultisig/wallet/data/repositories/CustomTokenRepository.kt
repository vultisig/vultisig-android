package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.CustomTokenDao
import com.vultisig.wallet.data.db.models.CustomTokenEntity
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal interface CustomTokenRepository {

    fun getAll(chainId: String): Flow<List<Coin>>

    suspend fun insert(coin: Coin)

    suspend fun remove(id: String)

    suspend fun findByContractAddress(contractAddress: String): Coin
}

internal class CustomTokenRepositoryImpl @Inject constructor(
    private val customTokenDao: CustomTokenDao
) : CustomTokenRepository {
    override fun getAll(chainId: String): Flow<List<Coin>> =
        customTokenDao.getAll(chainId).map { entity -> entity.map { it.toCoin() } }


    override suspend fun insert(coin: Coin) {
        customTokenDao.insert(coin.toCustomTokenEntity())
    }

    override suspend fun remove(id: String) {
        customTokenDao.remove(id)
    }

    override suspend fun findByContractAddress(contractAddress: String): Coin =
        customTokenDao.findByContractAddress(contractAddress).toCoin()
}

private fun CustomTokenEntity.toCoin() = Coin(
    chain = Chain.fromRaw(chain),
    ticker = ticker,
    logo = logo,
    address = address,
    decimal = decimals,
    hexPublicKey = hexPublicKey,
    contractAddress = contractAddress,
    isNativeToken = false,
    priceProviderID = priceProviderID
)

private fun Coin.toCustomTokenEntity() = CustomTokenEntity(
    id = "${this.ticker}-${this.chain.raw}",
    chain = this.chain.raw,
    ticker = this.ticker,
    address = this.address,
    decimals = this.decimal,
    hexPublicKey = this.hexPublicKey,
    priceProviderID = this.priceProviderID,
    contractAddress = this.contractAddress,
    logo = this.logo,
)