package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.CoinGeckoApi
import com.vultisig.wallet.data.api.CurrencyToPrice
import com.vultisig.wallet.data.api.LiQuestApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.VaultRedemptionResponseJson
import com.vultisig.wallet.data.db.dao.TokenPriceDao
import com.vultisig.wallet.data.db.models.TokenPriceEntity
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TokenId
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.evmChainId
import com.vultisig.wallet.data.models.settings.AppCurrency
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.supervisorScope
import timber.log.Timber

interface TokenPriceRepository {

    suspend fun getCachedPrice(tokenId: String, appCurrency: AppCurrency): BigDecimal?

    suspend fun getCachedPrices(
        tokenIds: List<String>,
        appCurrency: AppCurrency,
    ): List<Pair<String, BigDecimal>>

    fun getPrice(token: Coin, appCurrency: AppCurrency): Flow<BigDecimal>

    suspend fun refresh(tokens: List<Coin>)

    suspend fun getPriceByContactAddress(chainId: String, contractAddress: String): BigDecimal

    suspend fun getPriceByPriceProviderId(priceProviderId: String): BigDecimal
}

internal class TokenPriceRepositoryImpl
@Inject
constructor(
    private val appCurrencyRepository: AppCurrencyRepository,
    private val coinGeckoApi: CoinGeckoApi,
    private val liQuestApi: LiQuestApi,
    private val thorApi: ThorChainApi,
    private val mayaApi: MayaChainApi,
    private val tokenPriceDao: TokenPriceDao,
) : TokenPriceRepository {

    private val tokenIdToPrice = MutableStateFlow(mapOf<String, CurrencyToPrice>())

    override suspend fun getCachedPrice(tokenId: String, appCurrency: AppCurrency): BigDecimal? =
        tokenPriceDao.getTokenPrice(tokenId, appCurrency.ticker.lowercase())?.let { BigDecimal(it) }

    override suspend fun getCachedPrices(
        tokenIds: List<String>,
        appCurrency: AppCurrency,
    ): List<Pair<String, BigDecimal>> =
        tokenPriceDao.getTokenPrices(tokenIds, appCurrency.ticker.lowercase()).map {
            it.tokenId to BigDecimal(it.price)
        }

    @ExperimentalCoroutinesApi
    override fun getPrice(token: Coin, appCurrency: AppCurrency): Flow<BigDecimal> =
        tokenIdToPrice.map { prices ->
            // Fall back to the last-known persisted price when the in-memory map has no entry yet.
            // The map is empty on every cold start, so without this fallback a balance fetch that
            // decoupled from the price refresh would price fresh balances at $0 until the refresh
            // lands, flashing the cached fiat to zero. The cached price holds the last-known fiat
            // until the refresh updates the StateFlow.
            prices[token.id]?.get(appCurrency.ticker.lowercase())
                ?: getCachedPrice(token.id, appCurrency)
                ?: BigDecimal.ZERO
        }

    override suspend fun refresh(tokens: List<Coin>) {
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()
        val currencies = listOf(currency)

        val tokensByPriceProviderIds = tokens.groupBy { it.priceProviderID.lowercase() }

        val priceProviderIds = mutableListOf<String>()
        val chainContractAddresses = mutableMapOf<Chain, List<Coin>>()

        // sort tokens with contract address and price provider id to different lists
        tokens.forEach { token ->
            when {
                token.priceProviderID.isEmpty() &&
                    token.usdPrice?.let { it > BigDecimal.ZERO } == true -> {
                    val tetherPrice =
                        if (currency == AppCurrency.USD.ticker.lowercase()) {
                            1.toBigDecimal()
                        } else {
                            fetchTetherPrice()
                        }

                    val tokenIdToPrices: Map<TokenId, CurrencyToPrice> =
                        mapOf(token.id to mapOf(currency to token.usdPrice * tetherPrice))
                    savePrices(tokenIdToPrices, currency)
                }

                token.priceProviderID.isNotEmpty() -> {
                    priceProviderIds.add(token.priceProviderID)
                }

                token.contractAddress.isNotEmpty() -> {
                    val existingChain =
                        chainContractAddresses.getOrPut(token.chain) { mutableListOf() }
                    chainContractAddresses[token.chain] = existingChain + token
                }
            }
        }

        val pricesWithProviderIds =
            coinGeckoApi
                .getCryptoPrices(priceProviderIds, currencies)
                .asSequence()
                .mapNotNull { (priceProviderId, value) ->
                    val tokenIds =
                        tokensByPriceProviderIds[priceProviderId.lowercase()]?.map { it.id }
                    tokenIds?.map { tokenId -> tokenId to value }
                }
                .flatten()
                .toMap()

        savePrices(pricesWithProviderIds, currency)

        // Fall back to the contract-address lookup for EVM tokens whose priceProviderID returned no
        // price but which have a valid contract address (e.g. ezETH on Base, whose priceProviderID
        // is not a CoinGecko-recognized id). Without this, such tokens would never be priced.
        // Restricted to EVM so non-EVM contract formats (e.g. THORChain x/… tokens handled by
        // fetchThorContractPrices) aren't fanned out to CoinGecko/LI.FI per-contract calls.
        val pricedTokenIds = pricesWithProviderIds.keys
        tokens.forEach { token ->
            if (
                token.chain.standard == TokenStandard.EVM &&
                    token.priceProviderID.isNotEmpty() &&
                    token.contractAddress.isNotEmpty() &&
                    token.id !in pricedTokenIds
            ) {
                val existingChain = chainContractAddresses.getOrPut(token.chain) { mutableListOf() }
                chainContractAddresses[token.chain] = existingChain + token
            }
        }

        chainContractAddresses.map { (chain, tokens) ->
            // Resolve ids from this chain's tokens only: the same contractAddress can exist on
            // multiple chains (e.g. ezETH on Arbitrum, Base and Optimism), so a global
            // address->token map would collapse them onto a single id and leave the rest at $0.00.
            val tokenIdsByContractAddress =
                tokens.associate { it.contractAddress.lowercase() to it.id }
            val pricesWithContractAddress =
                fetchPricesWithContractAddress(
                        chain = chain,
                        contractAddresses = tokens.map { it.contractAddress },
                        currencies = currencies,
                    )
                    .asSequence()
                    .mapNotNull { (contractAddress, value) ->
                        val tokenId = tokenIdsByContractAddress[contractAddress.lowercase()]
                        if (
                            tokenId != null &&
                                value.filter { it.value != BigDecimal.ZERO }.isNotEmpty()
                        ) {
                            tokenId to value
                        } else null
                    }
                    .toMap()

            savePrices(pricesWithContractAddress, currency)
        }

        fetchThorPoolPrices(tokenList = tokens, currency = currency)

        fetchMayaPoolPrices(tokenList = tokens, currency = currency)

        fetchThorContractPrices(currency = currency, tokenList = tokens)
    }

    override suspend fun getPriceByContactAddress(
        chainId: String,
        contractAddress: String,
    ): BigDecimal {
        if (contractAddress.isEmpty()) return BigDecimal.ZERO
        val chain = runCatching { Chain.fromRaw(chainId) }.getOrNull() ?: return BigDecimal.ZERO
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()

        val price =
            nativeChainContractPrice(chain, contractAddress, currency)
                ?: fetchPriceWithContractAddress(chain, contractAddress, currency)
                    ?.values
                    ?.firstOrNull()

        // Only a real quote is worth keeping. A miss used to arrive here as a zero and be written
        // straight into Room, so every later cache read served it back as a confident "this token
        // is worth nothing" — and on the chains with no working contract source that poisoned row
        // was the only price the token would ever have. Anything not strictly positive is a miss:
        // a negative quote is nonsense no source should produce, and persisting one would be worse
        // than the zero this replaces.
        if (price == null || price.signum() <= 0) return BigDecimal.ZERO

        // Cache under the coin id every reader queries by. The row used to be keyed by the raw
        // contract address, which nothing ever reads back — so the write was dead and each reload
        // repeated the live lookup. A contract the catalogue doesn't carry has no id to key on and
        // is simply not cached, rather than written somewhere unreachable.
        Coins.findCuratedByContract(chain, contractAddress)?.let { coin ->
            savePrices(mapOf(coin.id to mapOf(currency to price)), currency)
        }
        return price
    }

    /**
     * Prices a contract on a chain that CoinGecko's contract endpoint and LI.FI both ignore.
     *
     * THORChain and Maya carry their own price sources — index NAV for the staking receipts, pool
     * depth for anything with a pool — and are exactly the chains DeFi positions live on, so a
     * contract lookup there has to go through them or it can only ever answer zero.
     */
    private suspend fun nativeChainContractPrice(
        chain: Chain,
        contractAddress: String,
        currency: String,
    ): BigDecimal? =
        try {
            when (chain) {
                Chain.ThorChain -> thorContractPrice(contractAddress, currency)
                Chain.MayaChain -> mayaContractPrice(contractAddress, currency)
                else -> null
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to price %s contract %s natively", chain, contractAddress)
            null
        }

    private suspend fun thorContractPrice(contractAddress: String, currency: String): BigDecimal? {
        // The receipt route is contained rather than allowed to propagate: it is one source of
        // several, and the NAV host going down should cost this denom its preferred price, not its
        // turn at the pool route below.
        val receiptPrice =
            try {
                thorReceiptPriceUsd(contractAddress, currency)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.w(e, "NAV price unavailable for %s, trying the pool", contractAddress)
                null
            }

        val priceUsd = receiptPrice ?: thorPoolPriceUsd(contractAddress) ?: return null
        if (priceUsd.signum() <= 0) return null

        // fetchTetherPrice answers a CoinGecko miss with ZERO, so multiplying blind would turn a
        // perfectly good USD price into a $0.00 that reads exactly like "we have no price" — and
        // for a non-USD user, only ever on the FX leg. Treat a missing rate as a failed lookup.
        val fxRate = tetherPriceFor(currency)
        if (fxRate.signum() <= 0) {
            Timber.w("No %s/USD rate available, leaving %s unpriced", currency, contractAddress)
            return null
        }
        return priceUsd * fxRate
    }

    /**
     * The curated liquid-bonding and index-receipt denoms, priced the way [fetchThorContractPrices]
     * prices them for vault-held coins: parity with RUNE for bRUNE, index NAV times the underlying
     * for the receipts. Returns null for any other denom so the pool route gets a turn.
     */
    private suspend fun thorReceiptPriceUsd(
        contractAddress: String,
        currency: String,
    ): BigDecimal? {
        val denom = contractAddress.lowercase()
        return when {
            denom == BRUNE_DENOM -> runePriceUsd(currency)
            denom == YBRUNE_DENOM -> navPerShare(BRUNE_STAKING_CONTRACT) * runePriceUsd(currency)
            denom == STAKING_TCY_DENOM -> navPerShare(STAKING_TCY_CONTRACT) * tcyPriceUsd(currency)
            denom.startsWith("x/nami") ->
                thorApi
                    .getThorchainTokenPriceByContract(
                        denom.substringAfter("nav-").substringBefore("-rcpt")
                    )
                    .data
                    .navPerShare
                    .toBigDecimalOrNull()

            else -> null
        }
    }

    private suspend fun navPerShare(contract: String): BigDecimal =
        navPerShareFromStatus(thorApi.getThorchainTokenPriceByContract(contract))

    /**
     * Every other THORChain denom — `x/ruji`, `thor.kuji`, the secured assets — off its pool's TOR
     * price, the same source [fetchThorPoolPrices] uses. The `x/` prefix is stripped for the
     * ticker-shaped fallback because a native denom's pool is listed as `thor.<ticker>`.
     */
    private suspend fun thorPoolPriceUsd(contractAddress: String): BigDecimal? {
        val denom = contractAddress.lowercase()
        val pools = thorApi.getPools().associate { it.asset.lowercase() to it.assetTorPrice }
        val torPrice =
            pools[mapThorPoolAsset(denom)]
                ?: pools["thor.${denom.removePrefix("x/")}"]
                ?: return null
        return torPrice.toBigDecimal(scale = 8)
    }

    /**
     * Maya has no CoinGecko asset platform and no LI.FI chain, so its non-native assets price off
     * pool depth. The contract is resolved back to its curated coin because the pool math needs the
     * asset's decimals, which the contract address alone doesn't carry.
     */
    private suspend fun mayaContractPrice(contractAddress: String, currency: String): BigDecimal? {
        val token =
            Coins.coins[Chain.MayaChain]?.firstOrNull {
                !it.isNativeToken && it.contractAddress.equals(contractAddress, ignoreCase = true)
            } ?: return null
        // `currency` travels lowercased through this class; fromTicker matches on the enum's
        // uppercase ticker, so it has to be raised back or every lookup silently resolves to null.
        val appCurrency = AppCurrency.fromTicker(currency.uppercase()) ?: return null
        return mayaPoolPrice(token, appCurrency)
    }

    override suspend fun getPriceByPriceProviderId(priceProviderId: String): BigDecimal {
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()
        val cryptoPrices = coinGeckoApi.getCryptoPrices(listOf(priceProviderId), listOf(currency))
        return cryptoPrices.values.firstOrNull()?.values?.firstOrNull() ?: BigDecimal.ZERO
    }

    private suspend fun savePrices(
        tokenIdToPrices: Map<TokenId, CurrencyToPrice>,
        currency: String,
    ) {
        val tokenIdToPricesFiltered =
            tokenIdToPrices.filter { (_, currencyToPrice) -> currencyToPrice.isNotEmpty() }
        tokenIdToPricesFiltered.forEach { (tokenId, currencyToPrice) ->
            currencyToPrice[currency]?.toPlainString()?.let { price ->
                tokenPriceDao.insertTokenPrice(
                    TokenPriceEntity(tokenId = tokenId, currency = currency, price = price)
                )
            }
        }

        tokenIdToPrice.update { it + tokenIdToPricesFiltered }
    }

    private suspend fun fetchPricesWithContractAddress(
        chain: Chain,
        contractAddresses: List<String>,
        currencies: List<String>,
    ): Map<String, CurrencyToPrice> {
        return coroutineScope {
            val coinGeckoContractsPrice =
                coinGeckoApi.getContractsPrice(
                    chain = chain,
                    contractAddresses = contractAddresses,
                    currencies = currencies,
                )
            val notInCoinGeckoTokens =
                contractAddresses.filterNot { address ->
                    coinGeckoContractsPrice.keys.any { key -> key.equals(address, false) }
                }

            // LI.FI only indexes EVM chains, so asking it about a THORChain/Maya/Cosmos contract
            // could only ever fail. Skip it rather than fan out calls whose one possible answer is
            // a miss. THORChain and Maya have their own route in nativeChainContractPrice; the
            // Cosmos chains have none, so a contract there stays unpriced — but unpriced and
            // unrecorded, rather than the zero this used to write down as a real quote.
            if (notInCoinGeckoTokens.isEmpty() || chain.evmChainId() == null) {
                return@coroutineScope coinGeckoContractsPrice
            }

            val tetherPrice = fetchTetherPrice()
            val currency = currencies.first()
            val lifiContractsPrice =
                notInCoinGeckoTokens
                    .map { contractAddress ->
                        async {
                            contractAddress to getLifiContractPriceInUsd(chain, contractAddress)
                        }
                    }
                    .awaitAll()
                    // Lifi quotes in USD, so convert with USDT into the local currency. A contract
                    // it can't price is dropped, not recorded as zero: a zero is indistinguishable
                    // from a real "worth nothing" quote, and callers persist what they are handed.
                    // A quote of literal 0 from Lifi is treated the same way, for the same reason.
                    .mapNotNull { (contractAddress, priceInUsd) ->
                        priceInUsd
                            ?.takeIf { it.signum() > 0 }
                            ?.let { contractAddress to mapOf(currency to it * tetherPrice) }
                    }
                    .toMap()
            coinGeckoContractsPrice + lifiContractsPrice
        }
    }

    private suspend fun getLifiContractPriceInUsd(chain: Chain, contract: String): BigDecimal? =
        try {
            BigDecimal(liQuestApi.getLifiContractPriceUsd(chain, contract).priceUsd)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to fetch Lifi price for %s contract: %s", chain, contract)
            null
        }

    private suspend fun fetchPriceWithContractAddress(
        chain: Chain,
        contractAddress: String,
        currency: String,
    ): CurrencyToPrice? =
        fetchPricesWithContractAddress(
                chain = chain,
                contractAddresses = listOf(contractAddress),
                currencies = listOf(currency),
            )
            .values
            .firstOrNull()

    private suspend fun fetchTetherPrice() = getPriceByPriceProviderId(TETHER_PRICE_PROVIDER_ID)

    /**
     * Currency-per-USD, for converting the USD-quoted sources (pool TOR prices, index NAV) into the
     * app currency. Skips the USDT round trip when the app currency already is USD.
     *
     * [currency] selects only between skipping and fetching: the fetch itself resolves the app
     * currency live, so a switch between the caller capturing [currency] and this call returning
     * would quote the rate in the newer one. Callers refetch wholesale on a currency change, which
     * is what keeps that window from mattering; it is not safe to widen the gap between capture and
     * use on the strength of this parameter alone. Returns ZERO when the rate can't be fetched —
     * callers must treat that as a failed lookup, not as a rate.
     */
    private suspend fun tetherPriceFor(currency: String): BigDecimal =
        if (currency.equals(AppCurrency.USD.ticker, ignoreCase = true)) BigDecimal.ONE
        else fetchTetherPrice()

    private suspend fun fetchThorPoolPrices(tokenList: List<Coin>, currency: String) {
        supervisorScope {
            // if we have any thorchain tokens, then fetch their pool prices
            val thorTokens = tokenList.filter { it.chain == Chain.ThorChain && !it.isNativeToken }
            if (thorTokens.isEmpty()) return@supervisorScope // no tokens, no api request

            val poolAssetToPriceMap =
                try {
                    thorApi.getPools().associate { it.asset.lowercase() to it.assetTorPrice }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "Failed to fetch prices from pools")
                    return@supervisorScope
                }

            val tetherPrice = tetherPriceFor(currency)

            val tokenIdToPrices =
                thorTokens
                    .asSequence()
                    .mapNotNull {
                        val mappedAsset = mapThorPoolAsset(it.contractAddress)
                        var priceUsd = poolAssetToPriceMap[mappedAsset]?.toBigDecimal(scale = 8)

                        // Fall back to ticker-based mapping for backwards compatibility
                        if (priceUsd == null) {
                            val tickerAsset = "thor.${it.ticker}".lowercase()
                            priceUsd = poolAssetToPriceMap[tickerAsset]?.toBigDecimal(scale = 8)
                        }

                        // No price, or a pool quoting zero: skip the token either way. savePrices
                        // only filters empty maps, so a zero would land in Room and every later
                        // cache read would serve it as a real "worth nothing" — permanently, since
                        // nothing invalidates a cached price.
                        if (priceUsd == null || priceUsd.signum() <= 0) {
                            return@mapNotNull null
                        }

                        // Since ninerealms provides prices in USD, we use the USDT rate to convert
                        // them into
                        // the selected currency
                        it.id to mapOf(currency to priceUsd * tetherPrice)
                    }
                    .toMap()

            savePrices(tokenIdToPrices, currency)
        }
    }

    private suspend fun fetchMayaPoolPrices(tokenList: List<Coin>, currency: String) {
        supervisorScope {
            val mayaTokens = tokenList.filter { it.chain == Chain.MayaChain && !it.isNativeToken }
            if (mayaTokens.isEmpty()) return@supervisorScope

            val appCurrency = appCurrencyRepository.currency.first()
            val tokenIdToPrices =
                mayaTokens
                    .mapNotNull { token ->
                        try {
                            mayaPoolPrice(token, appCurrency)?.let {
                                token.id to mapOf(currency to it)
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Timber.e(e, "Failed to fetch Maya pool price for %s", token.ticker)
                            null
                        }
                    }
                    .toMap()

            savePrices(tokenIdToPrices, currency)
        }
    }

    /**
     * A Maya asset's price in [currency], derived from its pool's depth: the pool quotes the asset
     * in CACAO, and CACAO carries the CoinGecko id that turns that into fiat. Null when the pool is
     * empty or CACAO has no price at all, so the caller drops the token rather than record a zero.
     */
    /**
     * CACAO in [currency]: the cached price when there is one, else a live fetch by its CoinGecko
     * id — the same cache-then-live shape [runePriceUsd] and [tcyPriceUsd] use for their
     * underlyings. Reading the cache alone left every Maya pool leg at $0.00 on a cold cache,
     * because the single-contract lookup has no refresh cycle ahead of it to populate CACAO first.
     */
    private suspend fun cacaoPrice(currency: AppCurrency): BigDecimal? {
        getCachedPrice(Coins.MayaChain.CACAO.id, currency)
            ?.takeIf { it > BigDecimal.ZERO }
            ?.let {
                return it
            }
        return coinGeckoApi
            .getCryptoPrices(
                listOf(Coins.MayaChain.CACAO.priceProviderID),
                listOf(currency.ticker.lowercase()),
            )
            .values
            .firstOrNull()
            ?.values
            ?.firstOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
    }

    private suspend fun mayaPoolPrice(token: Coin, currency: AppCurrency): BigDecimal? {
        val cacaoPrice = cacaoPrice(currency) ?: return null

        val pool = mayaApi.getPool("MAYA.${token.ticker}")
        val balanceCacao = pool.balanceCacao.toBigDecimal()
        val balanceAsset = pool.balanceAsset.toBigDecimal()
        // An empty side means the pool can't quote the asset at all. Both are checked: a zero CACAO
        // balance divides out to a price of 0, which the caller would go on to persist as though
        // the asset were genuinely worthless.
        if (balanceAsset <= BigDecimal.ZERO || balanceCacao <= BigDecimal.ZERO) return null

        val cacaoDecimals = BigDecimal.TEN.pow(CACAO_DECIMALS)
        val assetDecimals = BigDecimal.TEN.pow(token.decimal)
        val normalizedCacao = balanceCacao.divide(cacaoDecimals, 8, RoundingMode.HALF_UP)
        val normalizedAsset = balanceAsset.divide(assetDecimals, 8, RoundingMode.HALF_UP)
        val priceInCacao = normalizedCacao.divide(normalizedAsset, 8, RoundingMode.HALF_UP)

        // A pool so thin that the ratio rounds to zero at 8dp is a miss, not a valuation.
        return (priceInCacao * cacaoPrice).takeIf { it.signum() > 0 }
    }

    private suspend fun fetchThorContractPrices(tokenList: List<Coin>, currency: String) =
        supervisorScope {
            try {
                val thorTokens =
                    Coins.coins[Chain.ThorChain]?.filter {
                        it.contractAddress.startsWith("x/nami") ||
                            it.contractAddress == STAKING_TCY_DENOM ||
                            it.contractAddress == BRUNE_DENOM ||
                            it.contractAddress == YBRUNE_DENOM
                    } ?: emptyList()

                val matchingTokens =
                    tokenList.filter { token -> thorTokens.any { it.id.equals(token.id, true) } }

                if (matchingTokens.isEmpty()) return@supervisorScope

                val contracts =
                    matchingTokens.map {
                        val addr = it.contractAddress.lowercase()
                        when {
                            addr.startsWith("x/nami") ->
                                addr.substringAfter("nav-").substringBefore("-rcpt")
                            addr == STAKING_TCY_DENOM -> STAKING_TCY_CONTRACT
                            addr == YBRUNE_DENOM -> BRUNE_STAKING_CONTRACT
                            else -> it.contractAddress
                        }
                    }

                val tokenIds = matchingTokens.map { it.id }

                val tetherPrice = tetherPriceFor(currency)

                // bRUNE and ybRUNE both price off RUNE-in-USD. Fetch it once up front (only when a
                // RUNE-backed denom is present) so concurrent per-token async blocks don't each
                // miss
                // a cold cache and fire a duplicate live CoinGecko call for the same value.
                val runeUsdPrice =
                    if (
                        matchingTokens.any {
                            val denom = it.contractAddress.lowercase()
                            denom == BRUNE_DENOM || denom == YBRUNE_DENOM
                        }
                    ) {
                        // This runs outside the per-token async guards, so a failure here would
                        // abort the whole batch (NAMI, sTCY, …). Contain it and fall back to 0 —
                        // the zero-price filter below then drops bRUNE/ybRUNE for this cycle
                        // instead of overwriting their last-known good price.
                        try {
                            runePriceUsd(currency)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Timber.e(e, "Failed to fetch shared RUNE-in-USD price")
                            BigDecimal.ZERO
                        }
                    } else {
                        BigDecimal.ZERO
                    }

                val tokenIdToPrices = coroutineScope {
                    contracts
                        .zip(tokenIds)
                        .mapIndexed { index, (contract, tokenId) ->
                            async {
                                try {
                                    val token = matchingTokens[index]

                                    val priceUsd =
                                        when (token.contractAddress.lowercase()) {
                                            // bRUNE is ≥1:1 RUNE-backed with no THORChain pool, so
                                            // it tracks RUNE at parity.
                                            BRUNE_DENOM -> runeUsdPrice
                                            // ybRUNE is the auto-compounding bRUNE staking receipt:
                                            // NAV (liquid_bond_size / liquid_bond_shares) × bRUNE,
                                            // and bRUNE ≈ RUNE. Same mechanism as sTCY.
                                            YBRUNE_DENOM -> navPerShare(contract) * runeUsdPrice
                                            STAKING_TCY_DENOM ->
                                                navPerShare(contract) * tcyPriceUsd(currency)
                                            else -> {
                                                // For NAMI tokens, use navPerShare
                                                thorApi
                                                    .getThorchainTokenPriceByContract(contract)
                                                    .data
                                                    .navPerShare
                                                    .toBigDecimalOrNull() ?: BigDecimal.ZERO
                                            }
                                        }

                                    tokenId to mapOf(currency to priceUsd * tetherPrice)
                                } catch (e: Exception) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    Timber.e(e, "Failed to fetch price for contract: $contract")
                                    null
                                }
                            }
                        }
                        .awaitAll()
                        .filterNotNull()
                        // Drop zero prices rather than persist them. A transient failure (an
                        // unparseable NAV, a rate-limited RUNE price) yields 0, and savePrices
                        // only guards an empty currency map, so a $0 would overwrite the
                        // last-known good price. signum() (not `!= ZERO`) is used so a scaled
                        // zero like 0E-8 from a NAV division still counts as zero.
                        .filter { (_, prices) -> prices.values.any { it.signum() != 0 } }
                        .toMap()
                }

                savePrices(tokenIdToPrices, currency)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.e(t, "Could not update YTCY/YRUNE/sTCY/ybRUNE prices")
            }
        }

    // RUNE-in-USD, used to price the RUNE-backed bRUNE/ybRUNE denoms.
    // Cache rows are keyed by Coin.id ("RUNE-THORChain"), not priceProviderID ("thorchain"), so a
    // lookup by provider id can never hit. The cached "usd" row is also only written while the app
    // currency is USD and is never invalidated on a currency switch, so a non-USD user who once ran
    // USD would otherwise read a frozen stale quote — only trust the cache while we are actually in
    // USD, else fetch live. The live fallback fetches RUNE explicitly in USD
    // (getPriceByPriceProviderId
    // returns the app currency), and callers multiply by tetherPrice (currency-per-USD), so a
    // non-USD value here would double-apply FX.
    private suspend fun runePriceUsd(currency: String): BigDecimal {
        if (currency.equals(AppCurrency.USD.ticker, ignoreCase = true)) {
            getCachedPrice(Coins.ThorChain.RUNE.id, AppCurrency.USD)?.let {
                return it
            }
        }
        return fetchRunePriceUsdLive()
    }

    private suspend fun fetchRunePriceUsdLive(): BigDecimal =
        fetchCryptoPriceUsdLive(Coins.ThorChain.RUNE.priceProviderID)

    // TCY-in-USD, used to price the sTCY staking receipt. Same rules as runePriceUsd: cache rows
    // key
    // on Coin.id ("TCY-THORChain"), not the "tcy" priceProviderID, and the cached usd row is only
    // fresh while the app currency is USD. sTCY then multiplies this by NAV and the caller by
    // tetherPrice, so a non-USD or provider-id-keyed value here would double-apply FX.
    private suspend fun tcyPriceUsd(currency: String): BigDecimal {
        if (currency.equals(AppCurrency.USD.ticker, ignoreCase = true)) {
            getCachedPrice(Coins.ThorChain.TCY.id, AppCurrency.USD)?.let {
                return it
            }
        }
        return fetchCryptoPriceUsdLive(Coins.ThorChain.TCY.priceProviderID)
    }

    private suspend fun fetchCryptoPriceUsdLive(priceProviderId: String): BigDecimal =
        coinGeckoApi
            .getCryptoPrices(listOf(priceProviderId), listOf(AppCurrency.USD.ticker.lowercase()))
            .values
            .firstOrNull()
            ?.values
            ?.firstOrNull() ?: BigDecimal.ZERO

    // NAV per share from a `rujira-staking` `{"status":{}}` response:
    // liquid_bond_size / liquid_bond_shares, falling back to 1 for a genuine pre-bond state (both
    // fields present and zero). A malformed/empty 2xx leaves the fields at their "" default;
    // pricing
    // off that (nav = 1 → RUNE/TCY parity) would overwrite the accrued NAV price and slip past the
    // caller's zero-filter, so treat an unparseable size/shares as 0 (dropped) instead.
    private fun navPerShareFromStatus(vaultData: VaultRedemptionResponseJson): BigDecimal {
        val size = vaultData.data.liquidBondSize.toBigDecimalOrNull() ?: return BigDecimal.ZERO
        val shares = vaultData.data.liquidBondShares.toBigDecimalOrNull() ?: return BigDecimal.ZERO
        return if (shares > BigDecimal.ZERO) {
            size.divide(shares, 8, RoundingMode.DOWN)
        } else {
            BigDecimal.ONE
        }
    }

    companion object {
        private const val TETHER_PRICE_PROVIDER_ID = "tether"
        private const val CACAO_DECIMALS = 10

        // Single source of truth: the curated denoms in Coins.kt.
        private val BRUNE_DENOM = Coins.ThorChain.bRUNE.contractAddress
        private val YBRUNE_DENOM = Coins.ThorChain.ybRUNE.contractAddress
        private val STAKING_TCY_DENOM = Coins.ThorChain.sTCY.contractAddress
        private const val BRUNE_STAKING_CONTRACT =
            "thor179fex2rxd45caedmz4hxsnu42sw20lu0djyh4yukyh965sq8muuqptru2g"
        private const val STAKING_TCY_CONTRACT =
            "thor1z7ejlk5wk2pxh9nfwjzkkdnrq4p2f5rjcpudltv0gh282dwfz6nq9g2cr0"
    }

    private fun mapThorPoolAsset(contractAddress: String): String {
        val addr = contractAddress.lowercase()

        return try {
            when {
                // simple alphanumeric -> thor.<addr>
                addr.matches(Regex("^[a-z0-9]+$")) -> "thor.$addr"

                // single hyphen pair -> replace hyphen with dot (e.g. bcs-bnb -> bcs.bnb)
                addr.matches(Regex("^[a-z0-9]+-[a-z0-9]+$")) -> addr.replace("-", ".")

                // special x/… pattern: take the third-from-last segment as the prefix
                // and join the last two segments with '-' to preserve things like `usdc-0x...`
                addr.startsWith("x/") && addr.contains("-") -> {
                    val after = addr.substringAfter("x/")
                    val parts = after.split("-").filter { it.isNotEmpty() }
                    if (parts.size >= 3) {
                        val prefix = parts[parts.size - 3]
                        val tail = parts.subList(parts.size - 2, parts.size).joinToString("-")
                        "$prefix.$tail"
                    } else if (parts.size == 2) {
                        // fallback: a.b -> parts[0].parts[1]
                        "${parts[0]}.${parts[1]}"
                    } else {
                        // fallback to replacing hyphens with dots
                        after.replace("-", ".")
                    }
                }

                addr.matches(Regex("^[a-z0-9]+-[a-z0-9]+-0x[0-9a-f]+$")) -> {
                    val i = addr.indexOf('-')
                    addr.substring(0, i) + "." + addr.substring(i + 1)
                }

                // fallback: replace hyphens with dots
                else -> addr.replace("-", ".")
            }
        } catch (t: Throwable) {
            "thor.$addr"
        }
    }
}
