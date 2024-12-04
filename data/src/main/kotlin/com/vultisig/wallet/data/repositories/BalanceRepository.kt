package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.models.Chain.Arbitrum
import com.vultisig.wallet.data.models.Chain.Avalanche
import com.vultisig.wallet.data.models.Chain.Base
import com.vultisig.wallet.data.models.Chain.Bitcoin
import com.vultisig.wallet.data.models.Chain.BitcoinCash
import com.vultisig.wallet.data.models.Chain.Blast
import com.vultisig.wallet.data.models.Chain.BscChain
import com.vultisig.wallet.data.models.Chain.CronosChain
import com.vultisig.wallet.data.models.Chain.Dash
import com.vultisig.wallet.data.models.Chain.Dogecoin
import com.vultisig.wallet.data.models.Chain.Dydx
import com.vultisig.wallet.data.models.Chain.Ethereum
import com.vultisig.wallet.data.models.Chain.GaiaChain
import com.vultisig.wallet.data.models.Chain.Kujira
import com.vultisig.wallet.data.models.Chain.Litecoin
import com.vultisig.wallet.data.models.Chain.MayaChain
import com.vultisig.wallet.data.models.Chain.Noble
import com.vultisig.wallet.data.models.Chain.Optimism
import com.vultisig.wallet.data.models.Chain.Osmosis
import com.vultisig.wallet.data.models.Chain.Polkadot
import com.vultisig.wallet.data.models.Chain.Polygon
import com.vultisig.wallet.data.models.Chain.Solana
import com.vultisig.wallet.data.models.Chain.Sui
import com.vultisig.wallet.data.models.Chain.Terra
import com.vultisig.wallet.data.models.Chain.TerraClassic
import com.vultisig.wallet.data.models.Chain.ThorChain
import com.vultisig.wallet.data.models.Chain.Ton
import com.vultisig.wallet.data.models.Chain.ZkSync
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenBalanceWrapped
import com.vultisig.wallet.data.models.TokenValue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.zip
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject


interface BalanceRepository {

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
    private val suiApi: SuiApi,
    private val tonApi: TonApi,

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
                FiatValue(
                    BigDecimal.ZERO,
                    currency.ticker
                )
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
            ThorChain -> {
                val listCosmosBalance = thorChainApi.getBalance(address)
                val balance = listCosmosBalance
                    .find { it.denom.equals(coin.ticker, ignoreCase = true) }
                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            MayaChain -> {
                val listCosmosBalance = mayaChainApi.getBalance(address)
                val balance = listCosmosBalance
                    .find { it.denom.equals(coin.ticker, ignoreCase = true) }
                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            Bitcoin, BitcoinCash, Litecoin, Dogecoin, Dash -> {
                val balance = blockchairApi.getAddressInfo(coin.chain, address)?.address?.balance
                balance?.toBigInteger() ?: 0.toBigInteger()
            }

            Ethereum, BscChain, Avalanche, Base, Arbitrum, Polygon, Optimism,
            Blast, CronosChain, ZkSync -> {
                evmApiFactory.createEvmApi(coin.chain).getBalance(coin)
            }

            GaiaChain, Kujira, Dydx, Osmosis, Terra, Noble -> {
                val cosmosApi = cosmosApiFactory.createCosmosApi(coin.chain)

                val balance = if (coin.contractAddress.startsWith("terra")) {
                    cosmosApi.getWasmTokenBalance(address, coin.contractAddress)
                } else {
                    val listCosmosBalance = cosmosApi.getBalance(address)
                    listCosmosBalance
                        .find {
                            it.denom.equals(
                                "u${coin.ticker.lowercase()}",
                                ignoreCase = true
                            ) || it.denom.equals(
                                "a${coin.ticker.lowercase()}",
                                ignoreCase = true
                            ) || it.denom.equals(
                                coin.contractAddress,
                                ignoreCase = true,
                            )
                        }
                }

                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            TerraClassic -> {
                val cosmosApi = cosmosApiFactory.createCosmosApi(coin.chain)

                val balance = if (coin.contractAddress.startsWith("terra")) {
                    cosmosApi.getWasmTokenBalance(address, coin.contractAddress)
                } else {
                    val listCosmosBalance = cosmosApi.getBalance(address)
                    listCosmosBalance
                        .find {
                            it.denom.equals(coin.chain.feeUnit, ignoreCase = true) ||
                                    it.denom.equals(
                                        coin.contractAddress,
                                        ignoreCase = true,
                                    )
                        }
                }

                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            Solana -> {
                if (coin.isNativeToken) {
                    solanaApi.getBalance(address)
                } else {
                    splTokenRepository.getBalance(coin)
                        ?: splTokenRepository.getCachedBalance(coin)
                }
            }
            Polkadot -> polkadotApi.getBalance(address)

            Sui -> suiApi.getBalance(address)

            Ton -> tonApi.getBalance(address)

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