package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.SuiCoinMetadata
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.utils.NetworkException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import timber.log.Timber

/**
 * Auto-discovers the Sui coin types an address actually holds.
 *
 * Companion to [EvmCoinFinder], [CosmosBankCoinFinder] and [RippleTokenFinder], but reached from
 * `GetChainTokensUseCase` rather than `TokenRepository.getTokensWithBalance`: that path feeds the
 * background refresh worker, which enables everything it returns straight into the vault. Anyone
 * can mint a Sui coin and airdrop it, so a held coin type is offered in the token picker for the
 * user to enable — the same treatment Solana's SPL holdings already get on Android. iOS instead
 * adds every discovered Sui coin to the vault unprompted, behind a spam heuristic Android has no
 * equivalent of.
 *
 * Unlike iOS, which walks `suix_getOwnedObjects` and fetches each object individually, this reads
 * the already-paginated `suix_getAllCoins`: it returns coin objects only (no NFTs), so nothing is
 * lost to a first-page cutoff. Held types the curated [Coins] catalog already lists resolve to the
 * catalog entry, which carries the hand-verified ticker, logo and `priceProviderID`; the rest are
 * described by their on-chain `CoinMetadata`, and are dropped when that metadata is missing or
 * unusable rather than shown under a placeholder ticker at a guessed magnitude.
 *
 * Network failures are logged and yield an empty list, matching the sibling finders: a transient
 * blip must not wipe tokens the vault already holds, and the next refresh retries.
 *
 * Tracks vultisig/vultisig-android#5443.
 */
interface SuiTokenFinder {
    suspend fun find(address: String): List<Coin>
}

internal class SuiTokenFinderImpl @Inject constructor(private val suiApi: SuiApi) : SuiTokenFinder {

    override suspend fun find(address: String): List<Coin> {
        val heldCoinTypes = fetchHeldCoinTypes(address)
        if (heldCoinTypes.isEmpty()) return emptyList()

        // Cap in-flight metadata reads so a heavily airdropped address doesn't burst the shared
        // public Sui endpoint into throttling — a throttled read drops the coin it describes.
        val gate = Semaphore(MAX_CONCURRENT_METADATA_REQUESTS)
        return coroutineScope {
            heldCoinTypes
                .map { coinType ->
                    async { curatedCoin(coinType) ?: gate.withPermit { discoveredCoin(coinType) } }
                }
                .awaitAll()
                .filterNotNull()
        }
    }

    private suspend fun fetchHeldCoinTypes(address: String): List<String> {
        val coins =
            try {
                suiApi.getAllCoins(address)
            } catch (e: SocketTimeoutException) {
                Timber.e(e, "Sui getAllCoins timed out")
                return emptyList()
            } catch (e: NetworkException) {
                Timber.e(e, "Sui getAllCoins failed: status=%d", e.httpStatusCode)
                return emptyList()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Sui getAllCoins failed")
                return emptyList()
            }

        return coins
            .asSequence()
            .map { it.coinType }
            .filterNot { it.isBlank() || SuiHelper.isNativeSuiCoinType(it) }
            // An address holds one coin object per payment received, so a single coin type comes
            // back as many objects.
            .distinctBy { SuiHelper.normalizeSuiCoinType(it) }
            // Two coin types are free to publish the same symbol, and a Sui Coin.id is its ticker
            // alone — so the token list can only keep one of them. Ordering by coin type rather
            // than by the node's object order makes that always the same one.
            .sortedBy { SuiHelper.normalizeSuiCoinType(it) }
            .toList()
    }

    /**
     * The curated [Coins] catalog entry for [coinType], or `null` when the catalog does not list
     * it. Matched through [SuiHelper.isSameSuiCoinType] because a node may return a coin type's
     * package address zero-padded where the catalog stores it short: a raw string compare would
     * miss, and republish a known token under its on-chain symbol with no price source.
     */
    private fun curatedCoin(coinType: String): Coin? =
        Coins.coins[Chain.Sui]?.firstOrNull {
            !it.isNativeToken && SuiHelper.isSameSuiCoinType(it.contractAddress, coinType)
        }

    private suspend fun discoveredCoin(coinType: String): Coin? {
        val metadata = fetchMetadata(coinType) ?: return null

        val ticker = metadata.symbol.trim()
        // Decimals scale every amount the user reads and approves before signing, so a coin the
        // node cannot describe is dropped rather than surfaced at the wrong magnitude. On-chain the
        // field is a u8, so anything outside that range is a malformed response, not a real coin.
        if (ticker.isEmpty() || metadata.decimals !in VALID_DECIMALS) {
            Timber.d("Dropping sui coin %s: unusable metadata", coinType)
            return null
        }

        return Coin(
            chain = Chain.Sui,
            ticker = ticker,
            logo = metadata.iconUrl.orEmpty(),
            address = "",
            decimal = metadata.decimals,
            hexPublicKey = "",
            priceProviderID = "",
            // Kept in the form the node reported: every coin-type comparison normalizes both
            // sides, so canonicalizing it here would only hide which form the chain answered with.
            contractAddress = coinType,
            isNativeToken = false,
        )
    }

    private suspend fun fetchMetadata(coinType: String): SuiCoinMetadata? =
        try {
            suiApi.getCoinMetadata(coinType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Sui coin metadata fetch failed for %s", coinType)
            null
        }

    private companion object {
        const val MAX_CONCURRENT_METADATA_REQUESTS = 8
        val VALID_DECIMALS = 0..UByte.MAX_VALUE.toInt()
    }
}
