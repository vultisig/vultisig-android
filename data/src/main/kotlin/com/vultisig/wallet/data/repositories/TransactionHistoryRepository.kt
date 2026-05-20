package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.TransactionHistoryDao
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.models.CommonTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.buildTransactionHistoryId
import com.vultisig.wallet.data.models.toEntity
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

interface TransactionHistoryRepository {
    suspend fun recordTransaction(
        vaultId: String,
        txHash: String,
        txData: TransactionHistoryData,
        genericData: CommonTransactionHistoryData,
    )

    suspend fun updateTransactionStatus(chain: String, txHash: String, result: TransactionResult)

    suspend fun getTransaction(chain: String, txHash: String): TransactionHistoryEntity?

    fun observeTransactions(
        vaultId: String,
        type: TransactionHistoryType,
        chain: String? = null,
    ): Flow<List<TransactionHistoryEntity>>

    suspend fun getPendingTransactions(vaultId: String): List<TransactionHistoryEntity>

    suspend fun getAllPendingTransactions(): List<TransactionHistoryEntity>

    /** Merge backfill data with existing rows without wiping local metadata. */
    suspend fun upsertFromBackfill(entity: TransactionHistoryEntity)

    /** Explicit CONFIRMED -> FAILED demotion for chain reorganizations. */
    suspend fun demoteForReorg(chain: String, txHash: String, reason: String)

    /** Bump retry counter on a transient-status row (drives exponential backoff). */
    suspend fun incrementRetryCount(chain: String, txHash: String)
}

enum class TransactionHistoryType {
    OVERVIEW,
    SWAPS,
    SEND,
}

class TransactionHistoryRepositoryImpl @Inject constructor(private val dao: TransactionHistoryDao) :
    TransactionHistoryRepository {

    override suspend fun recordTransaction(
        vaultId: String,
        txHash: String,
        txData: TransactionHistoryData,
        genericData: CommonTransactionHistoryData,
    ) = dao.insert(transaction = txData.toEntity(genericData))

    override suspend fun updateTransactionStatus(
        chain: String,
        txHash: String,
        result: TransactionResult,
    ) {
        val id = buildTransactionHistoryId(chain, txHash)
        val now = System.currentTimeMillis()
        when (result) {
            TransactionResult.Confirmed ->
                dao.updateToConfirmed(id = id, confirmedAt = now, lastCheckedAt = now)
            is TransactionResult.Failed ->
                dao.updateToFailed(id = id, failureReason = result.reason, lastCheckedAt = now)
            is TransactionResult.Refunded ->
                dao.updateToRefunded(id = id, reason = result.reason, lastCheckedAt = now)
            TransactionResult.Pending ->
                dao.updateStatus(id = id, status = TransactionStatus.PENDING, lastCheckedAt = now)
            // NotFound is transient (indexer lag); TimedOut means the foreground poller gave up
            // but the tx may still confirm — both keep the row pollable for the background poller.
            TransactionResult.NotFound,
            TransactionResult.TimedOut ->
                dao.updateStatus(id = id, status = TransactionStatus.NotFound, lastCheckedAt = now)
        }
    }

    override suspend fun getTransaction(chain: String, txHash: String): TransactionHistoryEntity? =
        dao.getById(buildTransactionHistoryId(chain, txHash))

    override fun observeTransactions(
        vaultId: String,
        type: TransactionHistoryType,
        chain: String?,
    ): Flow<List<TransactionHistoryEntity>> {
        // Treat blank as unscoped so a round-tripped "" doesn't silently filter to zero rows.
        val effectiveChain = chain?.takeIf { it.isNotBlank() }
        return when (type) {
            TransactionHistoryType.OVERVIEW -> dao.observeAllByVault(vaultId, effectiveChain)
            TransactionHistoryType.SEND -> dao.observeSendByVault(vaultId, effectiveChain)
            TransactionHistoryType.SWAPS -> dao.observeSwapByVault(vaultId, effectiveChain)
        }
    }

    override suspend fun getPendingTransactions(vaultId: String): List<TransactionHistoryEntity> =
        dao.getPendingTransactions(vaultId)

    override suspend fun getAllPendingTransactions(): List<TransactionHistoryEntity> =
        dao.getAllPendingTransactions()

    override suspend fun upsertFromBackfill(entity: TransactionHistoryEntity) =
        dao.upsertFromBackfill(entity)

    override suspend fun demoteForReorg(chain: String, txHash: String, reason: String) {
        val id = buildTransactionHistoryId(chain, txHash)
        val now = System.currentTimeMillis()
        dao.demoteForReorg(id = id, reason = reason, lastCheckedAt = now)
        Timber.w("Reorg demotion: CONFIRMED -> FAILED for tx %s on %s — %s", txHash, chain, reason)
    }

    override suspend fun incrementRetryCount(chain: String, txHash: String) {
        val id = buildTransactionHistoryId(chain, txHash)
        val now = System.currentTimeMillis()
        dao.incrementRetryCount(id = id, lastCheckedAt = now)
    }
}
