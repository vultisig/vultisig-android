package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.isRippleNativeCurrency
import com.vultisig.wallet.data.models.rippleTokenContractAddress
import com.vultisig.wallet.data.models.toRippleTokenUnits
import com.vultisig.wallet.data.utils.NetworkException
import java.math.BigInteger
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Auto-discovers XRPL issued-currency (trust-line) holdings.
 *
 * Companion to [EvmCoinFinder] and [CosmosBankCoinFinder]: the chain-detail screen and the
 * background token-refresh worker both route through `TokenRepository.getTokensWithBalance`, which
 * delegates here for [Chain.Ripple]. Each `account_lines` entry with a positive balance is matched
 * against the curated [Coins] catalog by its `"<currency>.<issuer>"` pair and surfaced only when
 * the catalog lists it, so its logo and `priceProviderID` come along.
 *
 * Discovery is gated to the catalog on purpose: anyone can open a trust line and label its currency
 * `USD` or `USDT`, so an arbitrary held line is not evidence of a genuine asset. Unrecognized pairs
 * are dropped rather than shown as unverifiable rows; a new XRPL token is onboarded by adding it to
 * `Coins.Ripple.all`.
 *
 * A trust line can exist with a zero balance — holding one is how an account opts in to a currency
 * before ever receiving it — and lines the account issues rather than holds carry a negative
 * balance. Neither is a holding, so both are dropped; the line still counts toward the account's
 * `OwnerCount`, which the native-XRP reserve calculation in [RippleApi.getBalance] already folds
 * into spendable XRP.
 *
 * Network failures are logged and yield an empty list, matching the sibling finders: a transient
 * blip must not wipe tokens the vault already holds, and the next refresh retries.
 *
 * Tracks vultisig/vultisig-android#5210 and the SDK-side work in vultisig/vultisig-sdk#997.
 */
interface RippleTokenFinder {
    suspend fun find(address: String): List<Coin>
}

internal class RippleTokenFinderImpl @Inject constructor(private val rippleApi: RippleApi) :
    RippleTokenFinder {

    override suspend fun find(address: String): List<Coin> {
        val lines =
            try {
                rippleApi.fetchAccountLines(address)
            } catch (e: SocketTimeoutException) {
                Timber.e(e, "XRPL account_lines timed out")
                return emptyList()
            } catch (e: NetworkException) {
                Timber.e(e, "XRPL account_lines failed: status=%d", e.httpStatusCode)
                return emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "XRPL account_lines failed")
                return emptyList()
            }

        return lines
            .asSequence()
            .filterNot { isRippleNativeCurrency(it.currency) }
            .filter { it.balance.toRippleTokenUnits() > BigInteger.ZERO }
            .mapNotNull { line -> curatedCoin(line.currency, line.account) }
            // One issuer can appear twice only through a malformed response, but the asset list
            // keys rows on Coin.id, so collapse duplicates before they can collide there.
            .distinctBy { it.contractAddress }
            .toList()
    }

    /**
     * The curated [Coins] catalog entry for a `(currency, issuer)` trust line, or `null` when the
     * catalog does not list it. The line's currency is compared in its on-ledger form (3-char ASCII
     * or 40-char hex), which is exactly how a catalog `contractAddress` is stored, so the two match
     * without normalization.
     */
    private fun curatedCoin(currency: String, issuer: String): Coin? {
        val contractAddress = rippleTokenContractAddress(currency, issuer)
        return Coins.coins[Chain.Ripple]?.firstOrNull {
            !it.isNativeToken && it.contractAddress == contractAddress
        }
    }
}
