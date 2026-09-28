package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.models.paidFeeWei
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import javax.inject.Inject

/**
 * Reads a settled EVM transaction's receipt and stores the gas it actually paid on its history row,
 * so History stops showing the `maxFeePerGas × gasLimit` ceiling the row was recorded with. Only
 * [txHash]'s own receipt is read: an approval that preceded it is a separate transaction.
 */
fun interface RecordPaidEvmNetworkFeeUseCase {
    suspend operator fun invoke(chain: Chain, txHash: String)
}

class RecordPaidEvmNetworkFeeUseCaseImpl
@Inject
constructor(
    private val evmApiFactory: EvmApiFactory,
    private val transactionHistoryRepository: TransactionHistoryRepository,
) : RecordPaidEvmNetworkFeeUseCase {

    override suspend fun invoke(chain: Chain, txHash: String) {
        if (chain.standard != TokenStandard.EVM) return
        val feeWei =
            evmApiFactory.createEvmApi(chain).getTxStatus(txHash)?.result?.paidFeeWei() ?: return
        transactionHistoryRepository.recordPaidNetworkFee(chain.raw, txHash, feeWei)
    }
}
