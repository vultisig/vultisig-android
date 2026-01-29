package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.models.Chain
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

fun interface PollingTxStatusUseCase : (Chain, String) -> Flow<TransactionResult>

internal class PollingTxStatusUseCaseImpl @Inject constructor(
    private val txStatusConfigurationProvider: TxStatusConfigurationProvider,
    private val transactionStatusRepository: TransactionStatusRepository,
) : PollingTxStatusUseCase {

    override fun invoke( chain: Chain, txHash: String) = flow {
        val txStatusConfiguration = txStatusConfigurationProvider.getConfigurationForChain(chain)
        while (currentCoroutineContext().isActive) {
            try {
                val result = transactionStatusRepository.checkTransactionStatus(txHash, chain)
                emit(result)
                delay(txStatusConfiguration.pollIntervalSeconds.seconds)
            } catch (_: Exception) {
                delay(txStatusConfiguration.pollIntervalSeconds.seconds)
            }
        }
    }

}


