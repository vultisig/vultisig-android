package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.data.api.models.calculateResourceStats
import com.vultisig.wallet.data.api.models.thorchain.MergeAccount
import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingDeFiBalanceService
import com.vultisig.wallet.data.blockchain.ethereum.CircleDeFiBalanceService
import com.vultisig.wallet.data.blockchain.maya.MayaDeFiBalanceService
import com.vultisig.wallet.data.blockchain.model.DeFiBalance
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingDeFiBalanceService
import com.vultisig.wallet.data.blockchain.thorchain.ThorchainDeFiBalanceService
import com.vultisig.wallet.data.blockchain.ton.TonDeFiBalanceService
import com.vultisig.wallet.data.blockchain.tron.TronDeFiBalanceService
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
import com.vultisig.wallet.data.models.Chain.Mantle
import com.vultisig.wallet.data.models.Chain.MayaChain
import com.vultisig.wallet.data.models.Chain.Noble
import com.vultisig.wallet.data.models.Chain.Optimism
import com.vultisig.wallet.data.models.Chain.Osmosis
import com.vultisig.wallet.data.models.Chain.Polkadot
import com.vultisig.wallet.data.models.Chain.Polygon
import com.vultisig.wallet.data.models.Chain.Sei
import com.vultisig.wallet.data.models.Chain.Solana
import com.vultisig.wallet.data.models.Chain.Sui
import com.vultisig.wallet.data.models.Chain.Terra
import com.vultisig.wallet.data.models.Chain.TerraClassic
import com.vultisig.wallet.data.models.Chain.ThorChain
import com.vultisig.wallet.data.models.Chain.Ton
import com.vultisig.wallet.data.models.Chain.Zcash
import com.vultisig.wallet.data.models.Chain.ZkSync
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenBalance
import com.vultisig.wallet.data.models.TokenBalanceAndPrice
import com.vultisig.wallet.data.models.TokenBalanceWrapped
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.utils.SimpleCache
import com.vultisig.wallet.data.utils.scaledFor
import java.math.BigDecimal
import java.math.BigInteger
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.zip
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Interface for the BalanceRepository. */
interface BalanceRepository {

    suspend fun getUnstakableTcyAmount(address: String): String?

    suspend fun getCachedTokenBalanceAndPrice(address: String, coin: Coin): TokenBalanceAndPrice

    suspend fun getDeFiCachedTokeBalanceAndPrice(
        address: String,
        coin: Coin,
        vaultId: String,
    ): List<TokenBalanceAndPrice>

    suspend fun getCachedTokenBalances(
        addresses: List<String>,
        coins: List<Coin>,
    ): List<TokenBalanceWrapped>

    fun getTokenBalanceAndPrice(address: String, coin: Coin): Flow<TokenBalanceAndPrice>

    fun getTokenValue(address: String, coin: Coin): Flow<TokenValue>

    /**
     * Batch-fetches balances for every coin in [coins] (all sharing one EVM [address] and chain) in
     * a single Multicall3 round-trip, persists each to the Room cache (mirroring [getTokenValue]'s
     * write), and returns them keyed by [Coin.id] alongside the already-refreshed price. Collapses
     * the previous N+1 `eth_call`s per chain to one; falls back internally to per-token calls on
     * chains without a canonical Multicall3.
     */
    suspend fun getEvmTokenBalancesAndPrices(
        address: String,
        coins: List<Coin>,
    ): Map<TokenId, TokenBalanceAndPrice>

    fun getDefiTokenBalanceAndPrice(
        address: String,
        coin: Coin,
        vaultId: String,
    ): Flow<TokenBalanceAndPrice>

    fun getTronResourceDataSource(address: String): Flow<ResourceUsage>

    suspend fun getMergeTokenValue(address: String, chain: Chain): List<MergeAccount>

    suspend fun getTcyAutoCompoundAmount(address: String): String?

    suspend fun invalidateBalance(address: String, coin: Coin)

    suspend fun invalidateDeFiBalance(address: String, chain: Chain, vaultId: String)
}

internal class BalanceRepositoryImpl
@Inject
constructor(
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
    private val bittensorApi: BittensorApi,
    private val suiApi: SuiApi,
    private val tonApi: TonApi,
    private val rippleApi: RippleApi,
    private val tronApi: TronApi,
    private val cardanoApi: CardanoApi,
    private val tokenValueDao: TokenValueDao,
    private val thorchainDeFiBalanceService: ThorchainDeFiBalanceService,
    private val circleDeFiBalanceService: CircleDeFiBalanceService,
    private val mayaDeFiBalanceService: MayaDeFiBalanceService,
    private val tronDeFiBalanceService: TronDeFiBalanceService,
    private val tonDeFiBalanceService: TonDeFiBalanceService,
    private val cosmosStakingDeFiBalanceService: CosmosStakingDeFiBalanceService,
    private val solanaStakingDeFiBalanceService: SolanaStakingDeFiBalanceService,
) : BalanceRepository {

    private val defiBalanceCache = SimpleCache<String, List<DeFiBalance>>(12 * 1000)

    private val defiLocks = ConcurrentHashMap<String, Mutex>()

    private fun lockFor(address: String) = defiLocks.computeIfAbsent(address) { Mutex() }

    override suspend fun getUnstakableTcyAmount(address: String): String? {
        return thorChainApi.getUnstakableTcyAmount(address)
    }

    override suspend fun getTcyAutoCompoundAmount(address: String): String? {
        return thorChainApi.getTcyAutoCompoundAmount(address)
    }

    override suspend fun invalidateBalance(address: String, coin: Coin) {
        tokenValueDao.deleteTokenValue(
            chainId = coin.chain.id,
            address = address,
            ticker = coin.ticker,
        )
    }

    override suspend fun invalidateDeFiBalance(address: String, chain: Chain, vaultId: String) {
        val key = deFiCacheKey(address, chain, vaultId) ?: return
        val mutex = lockFor(key)
        mutex.withLock { defiBalanceCache.remove(key) }
    }

    private fun deFiCacheKey(address: String, chain: Chain, vaultId: String): String? =
        when (chain) {
            ThorChain,
            Ethereum -> address
            MayaChain,
            Chain.Tron,
            Chain.Ton,
            Solana,
            Chain.Terra,
            Chain.TerraClassic,
            Chain.Qbtc -> "${chain.id}:$vaultId:$address"
            else -> null
        }

    override suspend fun getCachedTokenBalanceAndPrice(
        address: String,
        coin: Coin,
    ): TokenBalanceAndPrice {
        val currency = appCurrencyRepository.currency.first()

        val tokenValue = getCachedTokenValue(address, coin)

        val price = tokenPriceRepository.getCachedPrice(coin.id, currency)

        val priceValue = price ?: BigDecimal.ZERO

        val fiatValue =
            if (tokenValue != null) {
                FiatValue(
                    tokenValue.decimal.multiply(priceValue).scaledFor(currency),
                    currency.ticker,
                )
            } else {
                null
            }

        return TokenBalanceAndPrice(
            tokenBalance = TokenBalance(tokenValue = tokenValue, fiatValue = fiatValue),
            // Keep the full-precision unit price; display formatting (incl. sub-cent prices like
            // LUNC) is handled by FiatValueToStringMapper. Scaling here would collapse it to $0.00.
            price = FiatValue(priceValue, currency.ticker),
        )
    }

    override suspend fun getDeFiCachedTokeBalanceAndPrice(
        address: String,
        coin: Coin,
        vaultId: String,
    ): List<TokenBalanceAndPrice> {
        val currency = appCurrencyRepository.currency.first()

        val defiCachedBalances =
            when (coin.chain) {
                ThorChain -> thorchainDeFiBalanceService.getCacheDeFiBalance(address, vaultId)
                Ethereum -> circleDeFiBalanceService.getCacheDeFiBalance(address, vaultId)
                MayaChain -> mayaDeFiBalanceService.getCacheDeFiBalance(address, vaultId)
                Chain.Tron -> tronDeFiBalanceService.getCacheDeFiBalance(address, vaultId)
                Chain.Ton -> tonDeFiBalanceService.getCacheDeFiBalance(address, vaultId)
                Solana -> solanaStakingDeFiBalanceService.getCacheDeFiBalance(address, vaultId)
                Chain.Terra,
                Chain.TerraClassic,
                Chain.Qbtc ->
                    cosmosStakingDeFiBalanceService.getCacheDeFiBalance(coin.chain, vaultId)

                else -> error("Not Supported ${coin.chain}")
            }

        val allBalances = defiCachedBalances.flatMap { it.balances }

        return allBalances.map { balance ->
            val tokenValue =
                TokenValue(
                    value = balance.amount,
                    unit = balance.coin.ticker,
                    decimals = balance.coin.decimal,
                )

            val price = tokenPriceRepository.getCachedPrice(balance.coin.id, currency)

            val fiatValue =
                if (price != null) {
                    FiatValue(
                        value = tokenValue.decimal.multiply(price).scaledFor(currency),
                        currency = currency.ticker,
                    )
                } else {
                    FiatValue(value = BigDecimal.ZERO, currency = currency.ticker)
                }

            TokenBalanceAndPrice(
                tokenBalance = TokenBalance(tokenValue = tokenValue, fiatValue = fiatValue),
                price =
                    if (price != null) {
                        FiatValue(price, currency.ticker)
                    } else {
                        null
                    },
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
            val tokenValue =
                TokenValue(
                    value = tokenEntity.tokenValue.toBigInteger(),
                    unit = tokenEntity.ticker,
                    decimals = coins.find { it.id == tokenEntity.tokenId }?.decimal ?: 0,
                )

            val fiatValue =
                if (price != null) {
                    FiatValue(
                        tokenValue.decimal.multiply(price).scaledFor(currency),
                        currency.ticker,
                    )
                } else {
                    FiatValue(BigDecimal.ZERO, currency.ticker)
                }

            TokenBalanceWrapped(
                tokenBalance = TokenBalance(tokenValue = tokenValue, fiatValue = fiatValue),
                address = tokenEntity.address,
                coinId = tokenEntity.tokenId,
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getTokenBalanceAndPrice(address: String, coin: Coin): Flow<TokenBalanceAndPrice> =
        appCurrencyRepository.currency.flatMapConcat { currency ->
            tokenPriceRepository.getPrice(coin, currency).zip(getTokenValue(address, coin)) {
                price,
                balance ->
                TokenBalanceAndPrice(
                    tokenBalance =
                        TokenBalance(
                            tokenValue = balance,
                            fiatValue =
                                FiatValue(
                                    value = balance.decimal.multiply(price).scaledFor(currency),
                                    currency = currency.ticker,
                                ),
                        ),
                    price = FiatValue(value = price, currency = currency.ticker),
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

        val defiBalance =
            defiBalances
                .flatMap { it.balances }
                .find { it.coin.id.equals(coin.id, ignoreCase = true) }

        val tokenValue =
            if (defiBalance != null) {
                TokenValue(value = defiBalance.amount, unit = coin.ticker, decimals = coin.decimal)
            } else {
                TokenValue(value = BigInteger.ZERO, unit = coin.ticker, decimals = coin.decimal)
            }

        val currency = appCurrencyRepository.currency.first()
        val price = tokenPriceRepository.getPrice(coin, currency).first()

        val fiatValue =
            FiatValue(
                value = tokenValue.decimal.multiply(price).scaledFor(currency),
                currency = currency.ticker,
            )

        emit(
            TokenBalanceAndPrice(
                tokenBalance = TokenBalance(tokenValue = tokenValue, fiatValue = fiatValue),
                price = FiatValue(value = price, currency = currency.ticker),
            )
        )
    }

    override fun getTronResourceDataSource(address: String): Flow<ResourceUsage> = flow {
        tronResourceDataSource.readTronResourceLimit(address)?.let { emit(it) }
        val tronReource = tronApi.getAccountResource(address).calculateResourceStats()
        emit(tronReource)
        tronResourceDataSource.setTronResourceLimit(address, tronReource)
    }

    private suspend fun getDeFiTokenValue(
        address: String,
        coin: Coin,
        vaultId: String,
    ): List<DeFiBalance> {
        val cacheKey =
            deFiCacheKey(address, coin.chain, vaultId) ?: error("Not supported ${coin.chain}")
        val mutex = lockFor(cacheKey)
        return mutex.withLock {
            defiBalanceCache.get(cacheKey)
                ?: run {
                    val remote = fetchRemoteDeFiBalance(address, coin.chain, vaultId)
                    defiBalanceCache.put(cacheKey, remote)
                    remote
                }
        }
    }

    private suspend fun fetchRemoteDeFiBalance(
        address: String,
        chain: Chain,
        vaultId: String,
    ): List<DeFiBalance> =
        when (chain) {
            ThorChain -> thorchainDeFiBalanceService.getRemoteDeFiBalance(address, vaultId)
            Ethereum -> circleDeFiBalanceService.getRemoteDeFiBalance(address, vaultId)
            MayaChain -> mayaDeFiBalanceService.getRemoteDeFiBalance(address, vaultId)
            Chain.Tron -> tronDeFiBalanceService.getRemoteDeFiBalance(address, vaultId)
            Chain.Ton -> tonDeFiBalanceService.getRemoteDeFiBalance(address, vaultId)
            Solana -> solanaStakingDeFiBalanceService.getRemoteDeFiBalance(address, vaultId)
            // Each staking chain has its own LCD (Terra / TerraClassic even share an address), so
            // the chain is passed explicitly.
            Chain.Terra,
            Chain.TerraClassic,
            Chain.Qbtc ->
                cosmosStakingDeFiBalanceService.getRemoteDeFiBalance(chain, address, vaultId)
            else -> error("Not supported $chain")
        }

    private suspend fun getCachedTokenValue(address: String, coin: Coin): TokenValue? =
        tokenValueDao
            .getTokenValue(chainId = coin.chain.id, address = address, ticker = coin.ticker)
            ?.let {
                TokenValue(value = it.toBigInteger(), unit = coin.ticker, decimals = coin.decimal)
            }

    override fun getTokenValue(address: String, coin: Coin): Flow<TokenValue> =
        flow {
                emit(
                    TokenValue(
                        when (coin.chain) {
                            ThorChain -> {
                                val listCosmosBalance = thorChainApi.getBalance(address)
                                val balance =
                                    listCosmosBalance.find {
                                        it.denom.equals(coin.ticker, ignoreCase = true) ||
                                            it.denom.equals(coin.contractAddress, ignoreCase = true)
                                    }
                                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
                            }

                            MayaChain -> {
                                val listCosmosBalance = mayaChainApi.getBalance(address)
                                val balance =
                                    listCosmosBalance.find {
                                        it.denom.equals(coin.ticker, ignoreCase = true)
                                    }
                                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
                            }

                            Bitcoin,
                            BitcoinCash,
                            Litecoin,
                            Dogecoin,
                            Dash,
                            Zcash -> {
                                val balance =
                                    blockchairApi
                                        .getAddressInfo(coin.chain, address)
                                        ?.address
                                        ?.balance
                                balance?.toBigInteger() ?: 0.toBigInteger()
                            }

                            Ethereum,
                            BscChain,
                            Avalanche,
                            Base,
                            Arbitrum,
                            Polygon,
                            Optimism,
                            Mantle,
                            Blast,
                            CronosChain,
                            ZkSync,
                            Sei,
                            Hyperliquid -> {
                                evmApiFactory
                                    .createEvmApi(coin.chain)
                                    .getBalance(coin.copy(address = address))
                            }

                            GaiaChain,
                            Kujira,
                            Dydx,
                            Osmosis,
                            Terra,
                            Noble,
                            Akash -> {
                                val cosmosApi = cosmosApiFactory.createCosmosApi(coin.chain)

                                val balance =
                                    if (coin.contractAddress.startsWith("terra")) {
                                        cosmosApi.getWasmTokenBalance(address, coin.contractAddress)
                                    } else {
                                        val listCosmosBalance = cosmosApi.getBalance(address)
                                        listCosmosBalance.find { it.hasValidDenom(coin) }
                                    }

                                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
                            }

                            Chain.Qbtc -> {
                                val cosmosApi = cosmosApiFactory.createCosmosApi(coin.chain)
                                val balances = cosmosApi.getBalance(address)
                                val balance = balances.find { it.denom == "qbtc" }
                                balance?.amount?.toBigInteger() ?: 0.toBigInteger()
                            }

                            TerraClassic -> {
                                val cosmosApi = cosmosApiFactory.createCosmosApi(coin.chain)

                                val balance =
                                    if (coin.contractAddress.startsWith("terra")) {
                                        cosmosApi.getWasmTokenBalance(address, coin.contractAddress)
                                    } else {
                                        val listCosmosBalance = cosmosApi.getBalance(address)
                                        listCosmosBalance.find {
                                            (coin.contractAddress.isEmpty() &&
                                                it.denom.equals(
                                                    coin.chain.feeUnit,
                                                    ignoreCase = true,
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
                            Chain.Bittensor -> bittensorApi.getBalance(address)

                            Sui -> suiApi.getBalance(address, coin.contractAddress)

                            Ton ->
                                if (coin.isNativeToken) {
                                    tonApi.getBalance(address)
                                } else {
                                    tonApi.getJettonBalance(address, coin.contractAddress)
                                }
                            Chain.Ripple -> rippleApi.getBalance(coin)
                            Chain.Tron -> tronApi.getBalance(coin)
                            Chain.Cardano -> cardanoApi.getBalance(coin)
                        },
                        coin.ticker,
                        coin.decimal,
                    )
                )
            }
            .onEach { tokenValue ->
                tokenValueDao.insertTokenValue(
                    TokenValueEntity(
                        chain = coin.chain.id,
                        address = address,
                        ticker = coin.ticker,
                        tokenValue = tokenValue.value.toString(),
                    )
                )
            }

    override suspend fun getEvmTokenBalancesAndPrices(
        address: String,
        coins: List<Coin>,
    ): Map<TokenId, TokenBalanceAndPrice> {
        if (coins.isEmpty()) return emptyMap()

        val chain = coins.first().chain
        require(coins.all { it.chain == chain }) {
            "getEvmTokenBalancesAndPrices: coins must share one chain"
        }
        require(chain.standard == TokenStandard.EVM) {
            "getEvmTokenBalancesAndPrices: non-EVM $chain"
        }

        val balances =
            evmApiFactory.createEvmApi(chain).getBalances(address, coins.map { it.contractAddress })

        val currency = appCurrencyRepository.currency.first()

        return coins
            .mapNotNull { coin ->
                // A missing key means the balance read failed (getBalances omits failed tokens):
                // skip
                // the write and the emission so the cached row is preserved rather than overwritten
                // with a fake 0 (#5308). A genuine on-chain zero is present in the map and still
                // persists below.
                val rawBalance = balances[coin.contractAddress] ?: return@mapNotNull null

                tokenValueDao.insertTokenValue(
                    TokenValueEntity(
                        chain = coin.chain.id,
                        address = address,
                        ticker = coin.ticker,
                        tokenValue = rawBalance.toString(),
                    )
                )

                val tokenValue =
                    TokenValue(value = rawBalance, unit = coin.ticker, decimals = coin.decimal)
                val price = tokenPriceRepository.getPrice(coin, currency).first()

                coin.id to
                    TokenBalanceAndPrice(
                        tokenBalance =
                            TokenBalance(
                                tokenValue = tokenValue,
                                fiatValue =
                                    FiatValue(
                                        value =
                                            tokenValue.decimal.multiply(price).scaledFor(currency),
                                        currency = currency.ticker,
                                    ),
                            ),
                        price = FiatValue(value = price, currency = currency.ticker),
                    )
            }
            .toMap()
    }

    override suspend fun getMergeTokenValue(address: String, chain: Chain): List<MergeAccount> {
        return thorChainApi.getRujiMergeBalances(address)
    }
}
