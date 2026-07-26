package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.PendingLimitOrderDao
import com.vultisig.wallet.data.db.models.PendingLimitOrderEntity
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.swap.limit.LimitSwapMemo
import com.vultisig.wallet.data.swap.limit.thorchainMemoAsset
import com.vultisig.wallet.data.swap.limit.toThorchainFixedPoint
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject

/**
 * Local store of placed THORChain limit orders (#4154). Phase 1 records on successful broadcast and
 * exposes a per-vault read; Phase 2 surfaces the list inside TX History.
 */
interface PendingLimitOrderRepository {
    /**
     * Records a placed order, deriving the target price and expiry from the signed `=<` [memo]. A
     * no-op when [memo] is not a limit memo. Persistence/mapping failures propagate to the caller,
     * which records this best-effort (a failure never breaks the keysign flow).
     *
     * @param sourceAmount the deposited amount in the source coin's native smallest units.
     */
    suspend fun record(
        vaultId: String,
        inboundTxHash: String,
        sourceCoin: Coin,
        sourceAmount: BigInteger,
        memo: String,
    )

    suspend fun getPendingOrders(vaultId: String): List<PendingLimitOrderEntity>
}

internal class PendingLimitOrderRepositoryImpl
@Inject
constructor(private val dao: PendingLimitOrderDao) : PendingLimitOrderRepository {

    override suspend fun record(
        vaultId: String,
        inboundTxHash: String,
        sourceCoin: Coin,
        sourceAmount: BigInteger,
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
                status = STATUS_PENDING,
            )
        )
    }

    override suspend fun getPendingOrders(vaultId: String): List<PendingLimitOrderEntity> =
        dao.getByVaultId(vaultId)

    private companion object {
        const val STATUS_PENDING = "pending"
    }
}
