package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.db.dao.TransactionHistoryDao
import com.vultisig.wallet.data.db.models.TransactionHistoryEntity
import com.vultisig.wallet.data.db.models.TransactionStatus
import com.vultisig.wallet.data.models.CommonTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.toEntity
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

interface TransactionHistoryRepository {
    suspend fun recordTransaction(vaultId: String, txHash: String, txData: TransactionHistoryData, genericData: CommonTransactionHistoryData)
    suspend fun updateTransactionStatus(txHash: String, result: TransactionResult)
    suspend fun getTransaction(txHash: String): TransactionHistoryEntity?
    fun observeTransactions(vaultId: String, type: TransactionHistoryType): Flow<List<TransactionHistoryEntity>>
    suspend fun getPendingTransactions(vaultId: String): List<TransactionHistoryEntity>
    suspend fun getAllPendingTransactions(): List<TransactionHistoryEntity>
}

enum class TransactionHistoryType {
    OVERVIEW, SWAPS, SEND
}

class TransactionHistoryRepositoryImpl @Inject constructor(
    private val dao: TransactionHistoryDao,
) : TransactionHistoryRepository {

    override suspend fun recordTransaction(
        vaultId: String,
        txHash: String,
        txData: TransactionHistoryData,
        genericData: CommonTransactionHistoryData
    ) = dao.insert(
        transaction = txData.toEntity(genericData)
    )


    override suspend fun updateTransactionStatus(
        txHash: String,
        result: TransactionResult
    ) {
        val now = System.currentTimeMillis()

        when (result) {
            is TransactionResult.Confirmed -> {
                dao.updateToConfirmed(
                    txHash = txHash,
                    confirmedAt = now,
                    lastCheckedAt = now
                )
            }
            is TransactionResult.Failed -> {
                dao.updateToFailed(
                    txHash = txHash,
                    failureReason = result.reason,
                    lastCheckedAt = now
                )
            }
            TransactionResult.Pending -> {
                dao.updateStatus(
                    txHash = txHash,
                    status = TransactionStatus.PENDING,
                    lastCheckedAt = now
                )
            }
            TransactionResult.NotFound -> {
                dao.updateToFailed(
                    txHash = txHash,
                    failureReason = "Transaction not found",
                    lastCheckedAt = now
                )
            }
        }
    }

    override suspend fun getTransaction(txHash: String): TransactionHistoryEntity? =
        dao.getByTxHash(txHash)


    override fun observeTransactions(
        vaultId: String,
        type: TransactionHistoryType
    ): Flow<List<TransactionHistoryEntity>> {
        return when (type) {
            TransactionHistoryType.OVERVIEW -> dao.observeAllByVault(vaultId)
            TransactionHistoryType.SEND -> dao.observeSendByVault(vaultId)
            TransactionHistoryType.SWAPS -> dao.observeSwapByVault(vaultId)
        }
    }

    override suspend fun getPendingTransactions(vaultId: String): List<TransactionHistoryEntity> =
        dao.getPendingTransactions(vaultId)


    override suspend fun getAllPendingTransactions(): List<TransactionHistoryEntity> =
        dao.getAllPendingTransactions()

}