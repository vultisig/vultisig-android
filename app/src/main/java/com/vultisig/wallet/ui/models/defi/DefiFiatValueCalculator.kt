package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import java.math.BigDecimal
import java.math.RoundingMode
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Prices DeFi position amounts in the user's currency.
 *
 * The Thorchain and Maya position screens each carried their own identical copy of this conversion;
 * it lives here so both share one implementation. A price lookup that fails yields a zero value
 * rather than propagating, so a position whose price is momentarily unavailable still renders its
 * underlying token amount instead of collapsing the whole tab.
 */
internal class DefiFiatValueCalculator
@Inject
constructor(private val tokenPriceRepository: TokenPriceRepository) {

    suspend fun createFiatValue(amount: BigDecimal, coin: Coin, currency: AppCurrency): FiatValue =
        convert(amount, currency) { priceOf(coin, currency) }

    /**
     * Prices a pool asset the vault does not hold as a [Coin], so there is no coin id to look up.
     *
     * The pool names its asset by chain and ticker, which is enough to find the curated [Coin] for
     * it — and going through that coin is what gives the asset its CoinGecko id. Without it the
     * lookup fell straight to the contract route, which for a native pool asset has no contract
     * address to look up at all, so every pool leg not already in the vault read as $0.00. The raw
     * cache key stays as the fallback for an asset the catalogue doesn't carry.
     */
    suspend fun createFiatValueFromPoolAsset(
        amount: BigDecimal,
        chain: Chain,
        ticker: String,
        contractAddress: String,
        currency: AppCurrency,
    ): FiatValue =
        convert(amount, currency) { priceOfPoolAsset(chain, ticker, contractAddress, currency) }

    /**
     * The price of one [coin] in [currency]: the cache, then the CoinGecko id, then the contract.
     *
     * Public because the Thorchain screen prices its LP legs against amounts it holds itself rather
     * than through [createFiatValue], and its own copy of this lookup drifted from this one. A
     * failed lookup is left to propagate, so each caller can decide what a missing price means for
     * what it is rendering.
     */
    suspend fun priceOf(coin: Coin, currency: AppCurrency): BigDecimal {
        tokenPriceRepository.getCachedPrice(tokenId = coin.id, appCurrency = currency)?.let {
            return it
        }

        // A NAV-priced receipt borrows the underlying asset's price-provider id — sTCY carries
        // TCY's `tcy` — so the id route would answer with raw TCY under sTCY's name, dropping
        // everything the position has compounded. Only the contract route applies the NAV
        // correction, so these denoms take that route and no other.
        if (!Coins.isNavPricedDenom(coin)) {
            priceByProviderId(coin, currency)?.let {
                return it
            }
        }

        return tokenPriceRepository.getPriceByContactAddress(
            chainId = coin.chain.id,
            contractAddress = coin.contractAddress,
            appCurrency = currency,
        )
    }

    /**
     * [createFiatValueFromPoolAsset]'s lookup, for a caller that needs the price itself rather than
     * a formatted value.
     */
    suspend fun priceOfPoolAsset(
        chain: Chain,
        ticker: String,
        contractAddress: String,
        currency: AppCurrency,
    ): BigDecimal {
        Coins.findCurated(chain, ticker, contractAddress)?.let {
            return priceOf(it, currency)
        }

        return tokenPriceRepository.getCachedPrice(
            tokenId = "$ticker-${chain.id}",
            appCurrency = currency,
        )
            ?: tokenPriceRepository.getPriceByContactAddress(
                chainId = chain.id,
                contractAddress = contractAddress,
                appCurrency = currency,
            )
    }

    /**
     * The CoinGecko-id route, tried before the contract-address one. THORChain has neither a
     * CoinGecko asset-platform id nor a LI.FI chain id, so the contract route can only ever return
     * zero for `x/…` denoms like RUJI — a position whose coin carries a price-provider id must go
     * through it or the card renders $0.00.
     *
     * [currency] is passed down rather than left to the repository to resolve, so the quote and the
     * label [convert] stamps on it can't come from two different currencies.
     */
    private suspend fun priceByProviderId(coin: Coin, currency: AppCurrency): BigDecimal? =
        coin.priceProviderID
            .takeIf { it.isNotEmpty() }
            ?.let { tokenPriceRepository.getPriceByPriceProviderId(it, currency) }
            ?.takeIf { it > BigDecimal.ZERO }

    private suspend fun convert(
        amount: BigDecimal,
        currency: AppCurrency,
        price: suspend () -> BigDecimal,
    ): FiatValue =
        try {
            // Scale-sensitive by design: only an exact BigDecimal.ZERO skips the price lookup.
            if (amount == BigDecimal.ZERO) {
                FiatValue(BigDecimal.ZERO, currency.ticker)
            } else {
                FiatValue(
                    value = amount.multiply(price()).setScale(2, RoundingMode.DOWN),
                    currency = currency.ticker,
                )
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t)
            FiatValue(value = BigDecimal.ZERO, currency = currency.ticker)
        }
}
