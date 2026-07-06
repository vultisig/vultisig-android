package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.tonUserFriendlyAddress
import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackRequest
import com.vultisig.wallet.data.api.models.quotes.SwapKitTrackResponseJson
import com.vultisig.wallet.data.api.swapAggregators.SwapKitApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import java.math.BigDecimal
import java.math.BigInteger
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

internal class SwapKitTrackingServiceImpl
@Inject
constructor(
    private val api: SwapKitApi,
    private val tonApi: TonApi,
    private val transactionHistoryRepository: TransactionHistoryRepository,
) : SwapKitTrackingService {

    override fun canTrack(chain: Chain): Boolean = SwapKitChainIdentifier.chainId(chain) != null

    override suspend fun checkSettlementStatus(
        broadcastHash: String,
        chain: Chain,
    ): TransactionResult {
        val chainId = SwapKitChainIdentifier.chainId(chain) ?: return TransactionResult.Pending
        return try {
            val response = api.track(SwapKitTrackRequest(hash = broadcastHash, chainId = chainId))
            val result = SwapKitTrackingStatusMapper.map(response)
            // Layer 1: distrust a deposit-leg-only "completed". A same-chain TON (Omniston) swap
            // reports the source deposit as "completed" with identical from/to assets while
            // settlement is still pending; only the deposit leg is verified, and an escrow refund
            // or a market-maker fill lands later. Resolve the real outcome on-chain rather than
            // flip the row to Success. Non-degenerate responses (distinct assets, other chains)
            // pass through unchanged.
            if (
                result == TransactionResult.Confirmed &&
                    chain == Chain.Ton &&
                    !response.fromAsset.isNullOrBlank() &&
                    response.fromAsset == response.toAsset
            ) {
                resolveTonSettlement(response, broadcastHash, chain)
            } else {
                result
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "SwapKit /track check failed for %s on %s", broadcastHash, chain.raw)
            TransactionResult.Pending
        }
    }

    /**
     * Resolves a same-chain TON (Omniston) swap's settlement on-chain from the persisted swap
     * context, since `/track` only ever describes the source deposit leg. Reads the deposit escrow
     * ([SwapKitTrackResponseJson.toAddress]) and source master
     * ([SwapKitTrackResponseJson.fromAsset]) from the `/track` response and the destination master
     * / native flag / user address from the persisted [SwapTransactionHistoryData]:
     * - an incoming transfer of the source jetton from the escrow → [TransactionResult.Refunded];
     * - an incoming transfer of the destination asset (jetton by master, or native value ≥ half the
     *   expected output to reject gas-excess dust) → [TransactionResult.Confirmed];
     * - neither yet, or missing context (e.g. a legacy row) → [TransactionResult.Pending] so
     *   polling continues.
     */
    private suspend fun resolveTonSettlement(
        response: SwapKitTrackResponseJson,
        broadcastHash: String,
        chain: Chain,
    ): TransactionResult {
        val entity = transactionHistoryRepository.getTransaction(chain.raw, broadcastHash)
        val payload =
            entity?.payload as? SwapTransactionHistoryData ?: return TransactionResult.Pending
        // Missing context (e.g. a legacy row without these fields) keeps the row in-flight.
        val userOwner =
            payload.fromAddress.takeIf { it.isNotBlank() } ?: return TransactionResult.Pending
        val escrow = response.toAddress?.let(::canonicalTon) ?: return TransactionResult.Pending
        val sourceMaster =
            response.fromAsset
                ?.substringAfter('-', "")
                ?.takeIf { it.isNotBlank() }
                ?.let(::canonicalTon) ?: return TransactionResult.Pending
        val startUtime = response.finalisedAt?.toLong() ?: (entity.timestamp / 1000)

        val incoming = tonApi.getIncomingJettonTransfers(userOwner, startUtime)
        if (incoming.any { it.jettonMaster == sourceMaster && it.senderOwner == escrow }) {
            return TransactionResult.Refunded("Omniston escrow refunded the deposit")
        }

        val filled =
            if (payload.toIsNative) {
                val threshold = nativeFillThreshold(payload.toAmountDecimal)
                threshold != null &&
                    tonApi.getMaxIncomingTonValue(userOwner, startUtime) >= threshold
            } else {
                val dstMaster =
                    payload.toContractAddress.takeIf { it.isNotBlank() }?.let(::canonicalTon)
                dstMaster != null && incoming.any { it.jettonMaster == dstMaster }
            }
        return if (filled) TransactionResult.Confirmed else TransactionResult.Pending
    }

    /** Canonicalizes a TON address to its user-friendly form, falling back to the raw value. */
    private fun canonicalTon(address: String): String = tonUserFriendlyAddress(address) ?: address

    /**
     * Half the expected native output in nanoton, or `null` when [expectedOut] isn't a positive
     * decimal. Guards the native-destination fill check against gas-excess dust returned by the
     * escrow.
     */
    private fun nativeFillThreshold(expectedOut: String): BigInteger? =
        expectedOut
            .toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
            ?.let {
                it.multiply(BigDecimal.TEN.pow(TON_NATIVE_DECIMALS))
                    .multiply(FILL_THRESHOLD_FRACTION)
                    .toBigInteger()
            }

    private companion object {
        const val TON_NATIVE_DECIMALS = 9
        val FILL_THRESHOLD_FRACTION: BigDecimal = BigDecimal("0.5")
    }
}
