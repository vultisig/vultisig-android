package com.vultisig.wallet.ui.models.defi

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
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
        convert(amount, currency) {
            tokenPriceRepository.getCachedPrice(tokenId = coin.id, appCurrency = currency)
                ?: priceByProviderId(coin)
                ?: tokenPriceRepository.getPriceByContactAddress(
                    coin.chain.id,
                    coin.contractAddress,
                )
        }

    /**
     * Prices a pool asset the vault does not hold as a [Coin], so there is no coin id to look up —
     * the cache key is rebuilt from the pool's ticker and chain instead.
     */
    suspend fun createFiatValueFromPoolAsset(
        amount: BigDecimal,
        chain: Chain,
        ticker: String,
        contractAddress: String,
        currency: AppCurrency,
    ): FiatValue =
        convert(amount, currency) {
            tokenPriceRepository.getCachedPrice(
                tokenId = "$ticker-${chain.id}",
                appCurrency = currency,
            ) ?: tokenPriceRepository.getPriceByContactAddress(chain.id, contractAddress)
        }

    /**
     * The CoinGecko-id route, tried before the contract-address one. THORChain has neither a
     * CoinGecko asset-platform id nor a LI.FI chain id, so the contract route can only ever return
     * zero for `x/…` denoms like RUJI — a position whose coin carries a price-provider id must go
     * through it or the card renders $0.00.
     */
    private suspend fun priceByProviderId(coin: Coin): BigDecimal? =
        coin.priceProviderID
            .takeIf { it.isNotEmpty() }
            ?.let { tokenPriceRepository.getPriceByPriceProviderId(it) }
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
