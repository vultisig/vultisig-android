package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject

internal class BittensorStatusProvider @Inject constructor(private val bittensorApi: BittensorApi) :
    TransactionStatusProvider {
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val tx = bittensorApi.getTxStatus(txHash) ?: return TransactionResult.Pending
        return when (tx.success) {
            true -> TransactionResult.Confirmed
            false -> TransactionResult.Failed("Transaction failed on Bittensor")
            null -> TransactionResult.Pending
        }
    }
}
