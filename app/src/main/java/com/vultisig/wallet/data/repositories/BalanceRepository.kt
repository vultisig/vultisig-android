package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenBalanceWrapped
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Chain.arbitrum
import com.vultisig.wallet.models.Chain.avalanche
import com.vultisig.wallet.models.Chain.base
import com.vultisig.wallet.models.Chain.bitcoin
import com.vultisig.wallet.models.Chain.bitcoinCash
import com.vultisig.wallet.models.Chain.bscChain
import com.vultisig.wallet.models.Chain.dash
import com.vultisig.wallet.models.Chain.dogecoin
import com.vultisig.wallet.models.Chain.dydx
import com.vultisig.wallet.models.Chain.ethereum
import com.vultisig.wallet.models.Chain.gaiaChain
import com.vultisig.wallet.models.Chain.kujira
import com.vultisig.wallet.models.Chain.litecoin
import com.vultisig.wallet.models.Chain.mayaChain
import com.vultisig.wallet.models.Chain.optimism
import com.vultisig.wallet.models.Chain.polkadot
import com.vultisig.wallet.models.Chain.polygon
import com.vultisig.wallet.models.Chain.solana
import com.vultisig.wallet.models.Chain.thorChain
import com.vultisig.wallet.models.Coin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.zip
import java.math.RoundingMode
import javax.inject.Inject


internal interface BalanceRepository {

    suspend fun getCachedTokenBalance(
        address: String,
        coin: Coin,
    ): TokenBalance

    suspend fun getCachedTokenBalances(
        addresses: List<String>,
        coins: List<Coin>
    ): List<TokenBalanceWrapped>

    fun getTokenBalance(
        address: String,
        coin: Coin,
    ): Flow<TokenBalance>

    fun getTokenValue(
        address: String,
        coin: Coin,
    ): Flow<TokenValue>

}

internal class BalanceRepositoryImpl @Inject constructor(
    private val thorChainApi: ThorChainApi,
    private val blockchairApi: BlockChairApi,
    private val evmApiFactory: EvmApiFactory,
    private val mayaChainApi: MayaChainApi,
    private val cosmosApiFactory: CosmosApiFactory,
    private val solanaApi: SolanaApi,
    private val splTokenRepository: SplTokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val polkadotApi: PolkadotApi,

    private val tokenValueDao: TokenValueDao,
) : BalanceRepository {

    override suspend fun getCachedTokenBalance(
        address: String,
        coin: Coin,
    ): TokenBalance {
        val currency = appCurrencyRepository.currency.first()

        val tokenValue = getCachedTokenValue(address, coin)

        val price = tokenPriceRepository.getCachedPrice(coin.id, currency)

        val fiatValue = if (tokenValue != null && price != null) {
            FiatValue(
                tokenValue.decimal
                    .multiply(price)
                    .setScale(2, RoundingMode.HALF_UP),
                currency.ticker
            )
        } else {
            null
        }

        return TokenBalance(
            tokenValue = tokenValue,
            fiatValue = fiatValue,
        )
    }

    override suspend fun getCachedTokenBalances(
        addresses: List<String>,
        coins: List<Coin>,
    ): List<TokenBalanceWrapped> {
        val currency = appCurrencyRepository.currency.first()

        val tokenEntities = tokenValueDao.getTokenValues(addresses)

        val prices = tokenPriceRepository.getCachedPrices(coins.map { it.id }, currency)

        return tokenEntities.map { tokenEntity ->
            val price = prices.find { it.first == tokenEntity.tokenId }?.second
            val tokenValue = TokenValue(
                value = tokenEntity.tokenValue.toBigInteger(),
                unit = tokenEntity.ticker,
                decimals = coins.find { it.id == tokenEntity.tokenId }?.decimal ?: 0,
            )

            val fiatValue = if (price != null) {
                FiatValue(
                    tokenValue.decimal
                        .multiply(price)
                        .setScale(2, RoundingMode.HALF_UP),
                    currency.ticker
                )
            } else {
                null
            }

            TokenBalanceWrapped(
                tokenBalance = TokenBalance(
                    tokenValue = tokenValue,
                    fiatValue = fiatValue,
                ),
                address = tokenEntity.address,
                coinId = tokenEntity.tokenId,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTokenBalance(
        address: String,
        coin: Coin,
    ): Flow<TokenBalance> =
        appCurrencyRepository
            .currency
            .flatMapConcat { currency ->
                tokenPriceRepository
                    .getPrice(coin, currency)
                    .zip(getTokenValue(address, coin)) { price, balance ->
                        TokenBalance(
                            tokenValue = balance,
                            fiatValue = FiatValue(
                                value = balance.decimal
                                    .multiply(price)
                                    .setScale(2, RoundingMode.HALF_UP),
                                currency = currency.ticker,
                            )
                        )
                    }
            }

    private suspend fun getCachedTokenValue(
        address: String,
        coin: Coin,
    ): TokenValue? = tokenValueDao.getTokenValue(
        chainId = coin.chain.id,
        address = address,
        ticker = coin.ticker,
    )?.let {
        TokenValue(
            value = it.toBigInteger(),
            unit = coin.ticker,
            decimals = coin.decimal,
        )
    }

    override fun getTokenValue(
        address: String,
        coin: Coin,
    ): Flow<TokenValue> = flow {
        emit(TokenValue(when (coin.chain) {
            thorChain -> {
                val listCosmosBalance = thorChainApi.getBalance(address)
                val balance = listCosmosBalance
                    .find { it.denom.equals(coin.ticker, ignoreCase = true) }
                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            mayaChain -> {
                val listCosmosBalance = mayaChainApi.getBalance(address)
                val balance = listCosmosBalance
                    .find { it.denom.equals(coin.ticker, ignoreCase = true) }
                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            bitcoin, bitcoinCash, litecoin, dogecoin, dash -> {
                val balance = blockchairApi.getAddressInfo(coin.chain, address)?.address?.balance
                balance?.toBigInteger() ?: 0.toBigInteger()
            }

            ethereum, bscChain, avalanche, base, arbitrum, polygon, optimism, Chain.blast, Chain.cronosChain -> {
                evmApiFactory.createEvmApi(coin.chain).getBalance(coin)
            }

            gaiaChain, kujira, dydx -> {
                val cosmosApi = cosmosApiFactory.createCosmosApi(coin.chain)
                val listCosmosBalance = cosmosApi.getBalance(address)
                val balance = listCosmosBalance
                    .find {
                        it.denom.equals(
                            "u${coin.ticker.lowercase()}",
                            ignoreCase = true
                        ) || it.denom.equals(
                            "a${coin.ticker.lowercase()}",
                            ignoreCase = true
                        )
                    }
                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            solana -> {
                if (coin.isNativeToken) {
                    solanaApi.getBalance(address).toBigInteger()
                } else {
                    splTokenRepository.getBalance(coin)
                        ?: splTokenRepository.getCachedBalance(coin)
                }
            }
            polkadot -> polkadotApi.getBalanace(address)

        }, coin.ticker, coin.decimal))
    }.onEach { tokenValue ->
        tokenValueDao.insertTokenValue(
            TokenValueEntity(
                chain = coin.chain.id,
                address = address,
                ticker = coin.ticker,
                tokenValue = tokenValue.value.toString(),
            )
        )
    }

}