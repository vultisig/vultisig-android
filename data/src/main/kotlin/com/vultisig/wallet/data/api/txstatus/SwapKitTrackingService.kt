package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackRequest
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

/**
 * Gates a SwapKit-routed swap's settlement on the destination-leg status reported by `POST /track`,
 * the same way [ThorMayaChainStatusProvider] gates THORChain/Maya swaps on Midgard. A cross-chain
 * SwapKit swap (e.g. ETH→SOL via Chainflip/NEAR) only reaches [TransactionResult.Confirmed] once
 * the destination asset has actually settled — not when the source-chain deposit confirms (which is
 * all the chain-routed [TransactionStatusProvider] for the source chain can see).
 *
 * Ported from iOS' `SwapKitTrackingService`, collapsed onto Android's existing poll-by-status
 * model: the per-swap poller lifecycle + ScenePhase handling iOS owns is already provided here by
 * [com.vultisig.wallet.data.usecases.RefreshPendingTransactionsUseCase] (tx history, with its own
 * exponential backoff) and by the keysign foreground poll (done screen).
 */
interface SwapKitTrackingService {

    /**
     * Whether [chain] is in SwapKit's `/track` catalogue. `false` means the caller should leave the
     * row on the existing source-chain status path rather than poll an unknown chainId.
     */
    fun canTrack(chain: Chain): Boolean

    /**
     * One-shot `/track` settlement check for [broadcastHash] on the source [chain]. Transient
     * network / decode errors return [TransactionResult.Pending] so the caller keeps polling
     * (mirrors [ThorMayaChainStatusProvider]). A chain SwapKit can't track also returns
     * [TransactionResult.Pending], leaving the row in-flight rather than flipping it terminal.
     */
    suspend fun checkSettlementStatus(broadcastHash: String, chain: Chain): TransactionResult
}

internal class SwapKitTrackingServiceImpl @Inject constructor(private val api: SwapKitApi) :
    SwapKitTrackingService {

    override fun canTrack(chain: Chain): Boolean = SwapKitChainIdentifier.chainId(chain) != null

    override suspend fun checkSettlementStatus(
        broadcastHash: String,
        chain: Chain,
    ): TransactionResult {
        val chainId = SwapKitChainIdentifier.chainId(chain) ?: return TransactionResult.Pending
        return try {
            val response = api.track(SwapKitTrackRequest(hash = broadcastHash, chainId = chainId))
            SwapKitTrackingStatusMapper.map(response)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "SwapKit /track check failed for %s on %s", broadcastHash, chain.raw)
            TransactionResult.Pending
        }
    }
}
