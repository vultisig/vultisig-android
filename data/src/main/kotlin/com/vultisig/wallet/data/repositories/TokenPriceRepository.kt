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
        val currency = appCurrencyRepository.currency.first().ticker.lowercase()
        val priceAndContract =
            fetchPriceWithContractAddress(Chain.fromRaw(chainId), contractAddress, currency)
        if (!priceAndContract.isNullOrEmpty()) {
            savePrices(mapOf(contractAddress to priceAndContract), currency)
            return priceAndContract.values.first()
        }
        return BigDecimal.ZERO
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

            notInCoinGeckoTokens.takeIf { it.isNotEmpty() }
                ?: return@coroutineScope coinGeckoContractsPrice

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
                    .associate { (contractAddress, priceInUsd) ->
                        // Since Lifi provides prices in USD, we use USDT to convert them into the
                        // local
                        // currency
                        contractAddress to
                            mapOf(currency to (priceInUsd?.times(tetherPrice) ?: BigDecimal.ZERO))
                    }
            coinGeckoContractsPrice + lifiContractsPrice
        }
    }

    private suspend fun getLifiContractPriceInUsd(chain: Chain, contract: String): BigDecimal? =
        try {
            BigDecimal(liQuestApi.getLifiContractPriceUsd(chain, contract).priceUsd)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
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

            val tickerUsd = AppCurrency.USD.ticker.lowercase()
            val tetherPrice =
                if (currency.equals(tickerUsd, ignoreCase = true)) 1.toBigDecimal()
                else fetchTetherPrice()

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

                        // If still no price found, skip this token
                        if (priceUsd == null) {
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

            val cacaoToken =
                tokenList.find { it.chain == Chain.MayaChain && it.isNativeToken }
                    ?: return@supervisorScope

            val userCurrency = appCurrencyRepository.currency.first()
            val cacaoPrice = getCachedPrice(cacaoToken.id, userCurrency) ?: return@supervisorScope
            if (cacaoPrice <= BigDecimal.ZERO) return@supervisorScope

            val tokenIdToPrices =
                mayaTokens
                    .mapNotNull { token ->
                        try {
                            val poolAsset = "MAYA.${token.ticker}"
                            val pool = mayaApi.getPool(poolAsset)
                            val balanceCacao = pool.balanceCacao.toBigDecimal()
                            val balanceAsset = pool.balanceAsset.toBigDecimal()
                            if (balanceAsset <= BigDecimal.ZERO) return@mapNotNull null

                            val cacaoDecimals = BigDecimal.TEN.pow(CACAO_DECIMALS)
                            val assetDecimals = BigDecimal.TEN.pow(token.decimal)
                            val normalizedCacao =
                                balanceCacao.divide(cacaoDecimals, 8, RoundingMode.HALF_UP)
                            val normalizedAsset =
                                balanceAsset.divide(assetDecimals, 8, RoundingMode.HALF_UP)
                            val priceInCacao =
                                normalizedCacao.divide(normalizedAsset, 8, RoundingMode.HALF_UP)

                            token.id to mapOf(currency to priceInCacao * cacaoPrice)
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                            Timber.e(e, "Failed to fetch Maya pool price for ${token.ticker}")
                            null
                        }
                    }
                    .toMap()

            savePrices(tokenIdToPrices, currency)
        }
    }

    private suspend fun fetchThorContractPrices(tokenList: List<Coin>, currency: String) =
        supervisorScope {
            try {
                val thorTokens =
                    Coins.coins[Chain.ThorChain]?.filter {
                        it.contractAddress.startsWith("x/nami") ||
                            it.contractAddress == "x/staking-tcy" ||
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
                            addr == "x/staking-tcy" ->
                                "thor1z7ejlk5wk2pxh9nfwjzkkdnrq4p2f5rjcpudltv0gh282dwfz6nq9g2cr0"
                            addr == YBRUNE_DENOM -> BRUNE_STAKING_CONTRACT
                            else -> it.contractAddress
                        }
                    }

                val tokenIds = matchingTokens.map { it.id }

                val tickerUsd = AppCurrency.USD.ticker.lowercase()
                val tetherPrice =
                    if (currency.equals(tickerUsd, ignoreCase = true)) {
                        BigDecimal.ONE
                    } else {
                        fetchTetherPrice()
                    }

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
                                            YBRUNE_DENOM -> {
                                                val nav =
                                                    navPerShareFromStatus(
                                                        thorApi.getThorchainTokenPriceByContract(
                                                            contract
                                                        )
                                                    )
                                                nav * runeUsdPrice
                                            }
                                            "x/staking-tcy" -> {
                                                val tcyPriceUSD = tcyPriceUsd(currency)
                                                val nav =
                                                    navPerShareFromStatus(
                                                        thorApi.getThorchainTokenPriceByContract(
                                                            contract
                                                        )
                                                    )
                                                nav * tcyPriceUSD
                                            }
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
        private const val BRUNE_STAKING_CONTRACT =
            "thor179fex2rxd45caedmz4hxsnu42sw20lu0djyh4yukyh965sq8muuqptru2g"
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
