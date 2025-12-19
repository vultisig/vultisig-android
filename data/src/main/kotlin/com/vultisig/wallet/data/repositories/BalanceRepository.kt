package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MergeAccount
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.data.api.models.calculateResourceStats
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.db.dao.TokenValueDao
import com.vultisig.wallet.data.db.models.TokenValueEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Chain.Akash
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
import com.vultisig.wallet.data.models.Chain.Hyperliquid
import com.vultisig.wallet.data.models.Chain.Kujira
import com.vultisig.wallet.data.models.Chain.Litecoin
import com.vultisig.wallet.data.models.Chain.MayaChain
import com.vultisig.wallet.data.models.Chain.Noble
import com.vultisig.wallet.data.models.Chain.Optimism
import com.vultisig.wallet.data.models.Chain.Osmosis
import com.vultisig.wallet.data.models.Chain.Polkadot
import com.vultisig.wallet.data.models.Chain.Polygon
import com.vultisig.wallet.data.models.Chain.Solana
import com.vultisig.wallet.data.models.Chain.Sei
import com.vultisig.wallet.data.models.Chain.Sui
import com.vultisig.wallet.data.models.Chain.Terra
import com.vultisig.wallet.data.models.Chain.TerraClassic
import com.vultisig.wallet.data.models.Chain.ThorChain
import com.vultisig.wallet.data.models.Chain.Ton
import com.vultisig.wallet.data.models.Chain.Zcash
import com.vultisig.wallet.data.models.Chain.ZkSync
import com.vultisig.wallet.data.models.Chain.Mantle
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenBalanceAndPrice
import com.vultisig.wallet.data.models.TokenBalanceWrapped
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.blockchain.ethereum.CircleDeFiBalanceService
import com.vultisig.wallet.data.blockchain.thorchain.ThorchainDeFiBalanceService
import com.vultisig.wallet.data.utils.SimpleCache
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Interface for the BalanceRepository.
 */
interface BalanceRepository {

    suspend fun getUnstakableTcyAmount(address: String): String?

    suspend fun getCachedTokenBalanceAndPrice(
        address: String,
        coin: Coin,
    ): TokenBalanceAndPrice

    suspend fun getDeFiCachedTokeBalanceAndPrice(
        address: String,
        coin: Coin,
        vaultId: String,
    ):  List<TokenBalanceAndPrice>

    suspend fun getCachedTokenBalances(
        addresses: List<String>,
        coins: List<Coin>
    ): List<TokenBalanceWrapped>

    fun getTokenBalanceAndPrice(
        address: String,
        coin: Coin,
    ): Flow<TokenBalanceAndPrice>

    fun getTokenValue(
        address: String,
        coin: Coin,
    ): Flow<TokenValue>

    fun getDefiTokenBalanceAndPrice(
        address: String,
        coin: Coin,
        vaultId: String,
    ): Flow<TokenBalanceAndPrice>

    fun getTronResourceDataSource(address: String ):Flow<ResourceUsage>

    suspend fun getMergeTokenValue(address: String, chain: Chain): List<MergeAccount>

    suspend fun getTcyAutoCompoundAmount(address: String): String?
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
    private val tronResourceDataSource: TronResourceDataSource,
    private val polkadotApi: PolkadotApi,
    private val suiApi: SuiApi,
    private val tonApi: TonApi,
    private val rippleApi: RippleApi,
    private val tronApi: TronApi,
    private val cardanoApi: CardanoApi,
    private val tokenValueDao: TokenValueDao,
    private val thorchainDeFiBalanceService: ThorchainDeFiBalanceService,
    private val circleDeFiBalanceService: CircleDeFiBalanceService,
) : BalanceRepository {

    private val defiBalanceCache = SimpleCache<String, List<DeFiBalance>>(12 * 1000)

    private val defiLocks = mutableMapOf<String, Mutex>()
    private fun lockFor(address: String) = defiLocks.getOrPut(address) { Mutex() }

    override suspend fun getUnstakableTcyAmount(address: String): String? {
        return thorChainApi.getUnstakableTcyAmount(address)
    }

    override suspend fun getTcyAutoCompoundAmount(address: String): String? {
        return thorChainApi.getTcyAutoCompoundAmount(address)
    }

    override suspend fun getCachedTokenBalanceAndPrice(
        address: String,
        coin: Coin,
    ): TokenBalanceAndPrice {
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

        return TokenBalanceAndPrice(
            tokenBalance = TokenBalance(
                tokenValue = tokenValue,
                fiatValue = fiatValue,
            ),
            price = if (price != null) FiatValue(
                price.setScale(2, RoundingMode.HALF_UP),
                currency.ticker
            ) else null
        )
    }

    override suspend fun getDeFiCachedTokeBalanceAndPrice(
        address: String,
        coin: Coin,
        vaultId: String,
    ): List<TokenBalanceAndPrice> {
        val currency = appCurrencyRepository.currency.first()

        val defiCachedBalances = when (coin.chain) {
            ThorChain -> thorchainDeFiBalanceService.getCacheDeFiBalance(address, vaultId)
            Ethereum -> circleDeFiBalanceService.getCacheDeFiBalance(address, vaultId)
            else -> error("Not Supported ${coin.chain}")
        }

        val allBalances = defiCachedBalances.flatMap { it.balances }
        
        return allBalances.map { balance ->
            val tokenValue = TokenValue(
                value = balance.amount,
                unit = balance.coin.ticker,
                decimals = balance.coin.decimal
            )
            
            val price = tokenPriceRepository.getCachedPrice(balance.coin.id, currency)
            
            val fiatValue = if (price != null) {
                FiatValue(
                    value = tokenValue.decimal
                        .multiply(price)
                        .setScale(2, RoundingMode.HALF_UP),
                    currency = currency.ticker
                )
            } else {
                FiatValue(
                    value = BigDecimal.ZERO,
                    currency = currency.ticker
                )
            }
            
            TokenBalanceAndPrice(
                tokenBalance = TokenBalance(
                    tokenValue = tokenValue,
                    fiatValue = fiatValue,
                ),
                price = if (price != null) {
                    FiatValue(
                        price.setScale(2, RoundingMode.HALF_UP),
                        currency.ticker
                    )
                } else {
                    null
                }
            )
        }
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
    override fun getTokenBalanceAndPrice(
        address: String,
        coin: Coin,
    ): Flow<TokenBalanceAndPrice> =
        appCurrencyRepository
            .currency
            .flatMapConcat { currency ->
                tokenPriceRepository
                    .getPrice(coin, currency)
                    .zip(getTokenValue(address, coin)) { price, balance ->
                        TokenBalanceAndPrice(
                            tokenBalance =TokenBalance(
                                tokenValue = balance,
                                fiatValue = FiatValue(
                                    value = balance.decimal
                                        .multiply(price)
                                        .setScale(2, RoundingMode.HALF_UP),
                                    currency = currency.ticker,
                                )
                            ),
                            price = FiatValue(
                                value = price.setScale(2, RoundingMode.HALF_UP),
                                currency = currency.ticker,
                            )
                        )
                    }
            }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getDefiTokenBalanceAndPrice(
        address: String,
        coin: Coin,
        vaultId: String,
    ): Flow<TokenBalanceAndPrice> = flow {
        val cacheValue = defiBalanceCache.get(address)
        val defiBalances = cacheValue ?: getDeFiTokenValue(address, coin, vaultId)
        
        val defiBalance = defiBalances
            .flatMap { it.balances }
            .find { it.coin.id.equals(coin.id, ignoreCase = true) }
        
        val tokenValue = if (defiBalance != null) {
            TokenValue(
                value = defiBalance.amount,
                unit = coin.ticker,
                decimals = coin.decimal,
            )
        } else {
            TokenValue(
                value = BigInteger.ZERO,
                unit = coin.ticker,
                decimals = coin.decimal,
            )
        }
        
        val currency = appCurrencyRepository.currency.first()
        val price = tokenPriceRepository.getPrice(coin, currency).first()
        
        val fiatValue = FiatValue(
            value = tokenValue.decimal
                .multiply(price)
                .setScale(2, RoundingMode.HALF_UP),
            currency = currency.ticker,
        )
        
        emit(TokenBalanceAndPrice(
            tokenBalance = TokenBalance(
                tokenValue = tokenValue,
                fiatValue = fiatValue
            ),
            price = FiatValue(
                value = price.setScale(2, RoundingMode.HALF_UP),
                currency = currency.ticker,
            )
        ))
    }

    override fun getTronResourceDataSource(
        address: String
    ): Flow<ResourceUsage> = flow {
        tronResourceDataSource.readTronResourceLimit(address)?.let {
            emit(it)
        }
        val tronReource = tronApi.getAccountResource(address).calculateResourceStats()
        emit(tronReource)
        tronResourceDataSource.setTronResourceLimit(
            address,
            tronReource
        )
    }

    private suspend fun getDeFiTokenValue(address: String, coin: Coin, vaultId: String): List<DeFiBalance> {
        return when (coin.chain) {
            ThorChain -> {
                val mutex = lockFor(address)
                mutex.withLock {
                    defiBalanceCache.get(address) ?: run {
                        val remote = thorchainDeFiBalanceService.getRemoteDeFiBalance(address, vaultId)
                        defiBalanceCache.put(address, remote)
                        remote
                    }
                }
            }
            Ethereum -> {
                val mutex = lockFor(address)
                mutex.withLock {
                    defiBalanceCache.get(address) ?: run {
                        val remote = circleDeFiBalanceService.getRemoteDeFiBalance(address, vaultId)
                        defiBalanceCache.put(address, remote)
                        remote
                    }
                }
            }
            else -> error("Not supported")
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
                    .find {
                        it.denom.equals(coin.ticker, ignoreCase = true) ||
                                it.denom.equals(coin.contractAddress, ignoreCase = true)
                    }
                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            MayaChain -> {
                val listCosmosBalance = mayaChainApi.getBalance(address)
                val balance = listCosmosBalance
                    .find { it.denom.equals(coin.ticker, ignoreCase = true) }
                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
            }

            Bitcoin, BitcoinCash, Litecoin, Dogecoin, Dash, Zcash -> {
                val balance = blockchairApi.getAddressInfo(coin.chain, address)?.address?.balance
                balance?.toBigInteger() ?: 0.toBigInteger()
            }

            Ethereum, BscChain, Avalanche, Base, Arbitrum, Polygon, Optimism, Mantle,
            Blast, CronosChain, ZkSync, Sei, Hyperliquid -> {
                evmApiFactory.createEvmApi(coin.chain).getBalance(coin)
            }

            GaiaChain, Kujira, Dydx, Osmosis, Terra, Noble, Akash -> {
                val cosmosApi = cosmosApiFactory.createCosmosApi(coin.chain)

                val balance = if (coin.contractAddress.startsWith("terra")) {
                    cosmosApi.getWasmTokenBalance(address, coin.contractAddress)
                } else {
                    val listCosmosBalance = cosmosApi.getBalance(address)
                    listCosmosBalance
                        .find {
                            it.hasValidDenom(coin)
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
                    listCosmosBalance.find {
                        (coin.contractAddress.isEmpty() &&
                                it.denom.equals(
                                    coin.chain.feeUnit,
                                    ignoreCase = true
                                )) ||
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

            Sui -> suiApi.getBalance(
                address,
                coin.contractAddress
            )

            Ton -> if (coin.isNativeToken) {
                tonApi.getBalance(address)
            } else {
                tonApi.getJettonBalance(address, coin.contractAddress)
            }
            Chain.Ripple -> rippleApi.getBalance(coin)
            Chain.Tron -> tronApi.getBalance(coin)
            Chain.Cardano -> cardanoApi.getBalance(coin)
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

    override suspend fun getMergeTokenValue(address: String, chain: Chain): List<MergeAccount> {
        return thorChainApi.getRujiMergeBalances(address)
    }
}