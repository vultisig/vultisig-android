package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import timber.log.Timber

interface RefreshPendingTransactionsUseCase {
    suspend operator fun invoke(vaultId: String)
}

class RefreshPendingTransactionsUseCaseImpl
@Inject
constructor(
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val transactionStatusRepository: TransactionStatusRepository,
    @IoDispatcher private val dispatcher: CoroutineDispatcher,
) : RefreshPendingTransactionsUseCase {

    override suspend fun invoke(vaultId: String) {
        withContext(dispatcher) {
            val pendingTransactions = transactionHistoryRepository.getPendingTransactions(vaultId)

            pendingTransactions
                .map { transaction ->
                    async {
                        try {
                            val chain = Chain.fromRaw(transaction.chain)
                            val result =
                                transactionStatusRepository.checkTransactionStatus(
                                    txHash = transaction.txHash,
                                    chain = chain,
                                )
                            transactionHistoryRepository.updateTransactionStatus(
                                transaction.txHash,
                                result,
                            )
                        } catch (e: Exception) {
                            Timber.e("Failed to refresh ${transaction.txHash}: ${e.message}")
                        }
                    }
                }
                .awaitAll()
        }
    }
}
