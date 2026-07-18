package com.vultisig.wallet.ui.models.keysign

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber

/** Upper bound for the pre-Verify price refresh so a hung request can't stall the Verify screen. */
private const val PRICE_REFRESH_TIMEOUT_MS = 10_000L

/**
 * Forces a bounded live price refresh for [tokens] before a join-keysign Verify screen converts
 * crypto amounts to fiat.
 *
 * A joining device otherwise prices from whatever rate is already persisted locally, and `getPrice`
 * only re-fetches when the cached value is exactly zero — so a stale-but-nonzero cached rate (seen
 * on DYDX) makes the fiat totals show a stale market price that diverges from the initiator.
 * Refreshing pulls the current CoinGecko rate (via each token's priceProviderID) so the joiner's
 * fiat reflects live pricing.
 *
 * The refresh is bounded by [PRICE_REFRESH_TIMEOUT_MS] because it can be the only network call
 * gating the Verify screen; a hung request must not stall it for the HTTP client's full socket
 * timeout. Failures (including a timeout) are logged and swallowed so pricing falls back to the
 * cached rate; [CancellationException] from the caller's scope still propagates.
 *
 * @param chainId only labels the log line on failure.
 */
internal suspend fun TokenPriceRepository.refreshVerifyPrices(tokens: List<Coin>, chainId: String) {
    withContext(Dispatchers.IO) {
        runCatching { withTimeout(PRICE_REFRESH_TIMEOUT_MS) { refresh(tokens) } }
            .onFailure {
                if (it is TimeoutCancellationException) {
                    Timber.w(it, "Price refresh timed out for %s", chainId)
                } else {
                    if (it is CancellationException) throw it
                    Timber.w(it, "Failed to refresh price for %s", chainId)
                }
            }
    }
}
