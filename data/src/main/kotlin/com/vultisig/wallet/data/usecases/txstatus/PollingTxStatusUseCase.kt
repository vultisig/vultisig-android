package com.vultisig.wallet.data.usecases.txstatus

import com.vultisig.wallet.data.models.Chain
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds

fun interface PollingTxStatusUseCase : (Chain, String) -> Flow<TransactionResult>

internal class PollingTxStatusUseCaseImpl @Inject constructor(
    private val txStatusConfigurationProvider: TxStatusConfigurationProvider,
    private val transactionStatusRepository: TransactionStatusRepository,
) : PollingTxStatusUseCase {

    override fun invoke(chain: Chain, txHash: String) = flow {
        val config = txStatusConfigurationProvider.getConfigurationForChain(chain)
        val startTime = System.currentTimeMillis()
        val timeoutMillis = config.maxWaitSeconds.seconds.inWholeMilliseconds

        var errorCount = 0

        while (currentCoroutineContext().isActive) {
            if (System.currentTimeMillis() - startTime >= timeoutMillis) {
                emit(TransactionResult.NotFound)
                return@flow
            }

            try {
                val result = transactionStatusRepository.checkTransactionStatus(txHash, chain)
                errorCount = 0
                emit(result)

                when (result) {
                    is TransactionResult.Confirmed,
                    is TransactionResult.Failed -> return@flow
                    else -> delay(config.pollIntervalSeconds.seconds)
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorCount++
                if (errorCount >= MAX_ERRORS) {
                    emit(TransactionResult.Failed("Network error: ${e.message}"))
                    return@flow
                }
                delay(config.pollIntervalSeconds.seconds)
            }
        }
    }

    private companion object {
        const val MAX_ERRORS = 5
    }
}

