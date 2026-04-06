package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusRepository
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
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
                        // CancellationException extends IllegalStateException -> RuntimeException
                        // ->
                        // Exception, so a bare catch (e: Exception) would swallow it and leak the
                        // in-flight coroutine after viewModelScope cancellation. Explicitly
                        // rethrow.
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
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to refresh %s", transaction.txHash)
                        }
                    }
                }
                .awaitAll()
        }
    }
}
