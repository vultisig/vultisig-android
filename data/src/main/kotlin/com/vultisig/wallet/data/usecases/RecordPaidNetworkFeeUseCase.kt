package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import javax.inject.Inject

/**
 * Stores the network fee a settled transaction actually paid on its history row, so History stops
 * showing the pre-sign estimate the row was recorded with: the `maxFeePerGas × gasLimit` ceiling on
 * EVM, the fixed 0.05 TON reservation on TON.
 */
fun interface RecordPaidNetworkFeeUseCase {
    suspend operator fun invoke(chain: Chain, txHash: String)
}

class RecordPaidNetworkFeeUseCaseImpl
@Inject
constructor(
    private val fetchPaidNetworkFee: FetchPaidNetworkFeeUseCase,
    private val transactionHistoryRepository: TransactionHistoryRepository,
) : RecordPaidNetworkFeeUseCase {

    override suspend fun invoke(chain: Chain, txHash: String) {
        val paidFee = fetchPaidNetworkFee(chain, txHash) ?: return
        transactionHistoryRepository.recordPaidNetworkFee(chain.raw, txHash, paidFee)
    }
}
