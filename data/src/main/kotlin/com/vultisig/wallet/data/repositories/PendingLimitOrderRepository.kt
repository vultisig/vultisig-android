package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.PendingLimitOrderDao
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.LimitOrderStatus
import com.vultisig.wallet.data.swap.limit.LimitSwapMemo
import com.vultisig.wallet.data.swap.limit.thorchainCancelMemoAsset
import com.vultisig.wallet.data.swap.limit.thorchainMemoAsset
import com.vultisig.wallet.data.swap.limit.toThorchainFixedPoint
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * Local store of placed THORChain limit orders (#4154).
 *
 * This table is authoritative for an order's state: THORChain exposes a resting order only through
 * its queue (which never says why an order left) and through Midgard (which indexes the closure),
 * so the order's own history — what was signed, what has been observed, whether a cancel was sent —
 * exists nowhere else.
 */
interface PendingLimitOrderRepository {
    /**
     * Records a placed order, deriving the target price and expiry from the signed `=<` [memo]. A
     * no-op when [memo] is not a limit memo. Persistence/mapping failures propagate to the caller,
     * which records this best-effort (a failure never breaks the keysign flow).
     *
     * @param sourceAmount the deposited amount in the source coin's native smallest units.
     * @param sourceAddress the address the deposit was sent from — the key THORChain's queue is
     *   scoped by, so without it the order cannot be tracked.
     * @param targetCoin the bought coin, used only to record the target asset's FULL spelling for a
     *   future cancel. Null degrades to "cancelling blocked until the queue reports the asset
     *   itself", never to a guessed spelling.
     */
    suspend fun record(
        vaultId: String,
        inboundTxHash: String,
        sourceCoin: Coin,
        sourceAmount: BigInteger,
        sourceAddress: String,
        targetCoin: Coin?,
        memo: String,
    )

    suspend fun getPendingOrders(vaultId: String): List<PendingLimitOrderEntity>

    /** Every order of the vault, newest first, as a live query. */
    fun observeOrders(vaultId: String): Flow<List<PendingLimitOrderEntity>>

    /** Orders still resting (`pending` or `cancelling`) — the ones the tracker has to poll. */
    suspend fun getOpenOrders(vaultId: String): List<PendingLimitOrderEntity>

    suspend fun getOrder(inboundTxHash: String): PendingLimitOrderEntity?

    /** Record what a queue poll observed about a still-resting order. */
    suspend fun recordObservation(
        inboundTxHash: String,
        depositAmount: String?,
        filledInAmount: String?,
        filledOutAmount: String?,
        observedTradeTarget: String?,
        observedSourceAsset: String?,
        observedTargetAsset: String?,
        timeToExpiryBlocks: Int?,
        observedAt: Long,
    )

    /** Move the order to a status resolved from THORChain's own account of the closure. */
    suspend fun recordStatus(inboundTxHash: String, status: LimitOrderStatus)

    /**
     * Record that a `m=<` cancel for this order has broadcast, moving it to
     * [LimitOrderStatus.Cancelling] — a statement about our transaction, never about the order.
     */
    suspend fun recordCancelBroadcast(inboundTxHash: String, cancelTxHash: String)

    /** Withdraw a cancel record whose transaction the chain refused. */
    suspend fun clearCancelBroadcast(inboundTxHash: String, expecting: String)

    /** Mark a recorded cancel as confirmed on its own chain. */
    suspend fun confirmCancelBroadcast(inboundTxHash: String, expecting: String)
}

internal class PendingLimitOrderRepositoryImpl
@Inject
constructor(private val dao: PendingLimitOrderDao) : PendingLimitOrderRepository {

    override suspend fun record(
        vaultId: String,
        inboundTxHash: String,
        sourceCoin: Coin,
        sourceAmount: BigInteger,
        sourceAddress: String,
        targetCoin: Coin?,
        memo: String,
    ) {
        val parsed = LimitSwapMemo.parse(memo) ?: return

        // target price = LIM / source_amount, both in THORChain's 1e8 scale (buy per sell unit).
        val sourceAmount1e8 = toThorchainFixedPoint(sourceAmount, sourceCoin.decimal)
        val targetPrice =
            if (sourceAmount1e8.signum() > 0) {
                BigDecimal(parsed.limit)
                    .divide(BigDecimal(sourceAmount1e8), 12, RoundingMode.HALF_UP)
                    .stripTrailingZeros()
                    .toPlainString()
            } else {
                "0"
            }

        dao.insert(
            PendingLimitOrderEntity(
                inboundTxHash = inboundTxHash,
                vaultId = vaultId,
                // Let a mapping failure propagate rather than store an unusable empty source_asset;
                // the caller (KeysignViewModel) already records this best-effort.
                sourceAsset = sourceCoin.thorchainMemoAsset(),
                sourceAmount = sourceAmount.toString(),
                targetAsset = parsed.targetAsset,
                destAddr = parsed.destAddr,
                targetPrice = targetPrice,
                expiryBlocks = parsed.expiryBlocks,
                createdAt = System.currentTimeMillis(),
                status = LimitOrderStatus.Pending.raw,
                sourceChain = sourceCoin.chain.raw,
                sourceDecimals = sourceCoin.decimal,
                sourceAddress = sourceAddress,
                sourceTicker = sourceCoin.ticker,
                targetTicker = targetCoin?.ticker,
                // Signing time is the only moment these are all known exactly, and they are what a
                // future cancel memo must reproduce. A null keeps the order uncancellable rather
                // than cancelled with guessed values.
                sourceAmount1e8 = sourceAmount1e8.toString(),
                tradeTarget = parsed.limit.toString(),
                sourceAssetFull = cancelMemoAssetOrNull(sourceCoin),
                targetAssetFull = targetCoin?.let(::cancelMemoAssetOrNull),
            )
        )
    }

    /**
     * The coin's full cancel-memo spelling, or null when it cannot be derived.
     *
     * Never fatal: a missing full spelling only blocks CANCELLING the order — and the queue's own
     * report can still supply it later — whereas throwing here would lose the entire record of an
     * order the user has already paid to place.
     */
    private fun cancelMemoAssetOrNull(coin: Coin): String? =
        try {
            coin.thorchainCancelMemoAsset()
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Could not derive the cancel-memo asset for %s", coin.ticker)
            null
        }

    override suspend fun getPendingOrders(vaultId: String): List<PendingLimitOrderEntity> =
        dao.getByVaultId(vaultId)

    override fun observeOrders(vaultId: String): Flow<List<PendingLimitOrderEntity>> =
        dao.observeByVaultId(vaultId)

    override suspend fun getOpenOrders(vaultId: String): List<PendingLimitOrderEntity> =
        dao.getOpenByVaultId(vaultId)

    override suspend fun getOrder(inboundTxHash: String): PendingLimitOrderEntity? =
        dao.getByTxHash(inboundTxHash)

    override suspend fun recordObservation(
        inboundTxHash: String,
        depositAmount: String?,
        filledInAmount: String?,
        filledOutAmount: String?,
        observedTradeTarget: String?,
        observedSourceAsset: String?,
        observedTargetAsset: String?,
        timeToExpiryBlocks: Int?,
        observedAt: Long,
    ) {
        dao.recordObservation(
            txHash = inboundTxHash,
            depositAmount = depositAmount,
            filledInAmount = filledInAmount,
            filledOutAmount = filledOutAmount,
            observedTradeTarget = observedTradeTarget,
            // A blank asset is not an observation. Dropped rather than stored, so it cannot
            // overwrite a spelling a cancel memo could actually be built from.
            observedSourceAsset = observedSourceAsset?.takeIf { it.isNotBlank() },
            observedTargetAsset = observedTargetAsset?.takeIf { it.isNotBlank() },
            timeToExpiryBlocks = timeToExpiryBlocks,
            observedAt = observedAt,
        )
    }

    override suspend fun recordStatus(inboundTxHash: String, status: LimitOrderStatus) {
        dao.recordStatus(inboundTxHash, status.raw)
    }

    override suspend fun recordCancelBroadcast(inboundTxHash: String, cancelTxHash: String) {
        dao.recordCancelBroadcast(inboundTxHash, cancelTxHash)
    }

    override suspend fun clearCancelBroadcast(inboundTxHash: String, expecting: String) {
        dao.clearCancelBroadcast(inboundTxHash, expecting)
    }

    override suspend fun confirmCancelBroadcast(inboundTxHash: String, expecting: String) {
        dao.confirmCancelBroadcast(inboundTxHash, expecting)
    }
}
