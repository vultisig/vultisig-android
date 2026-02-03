package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject


internal class SolanaStatusProvider @Inject constructor(
    private val solanaApi: SolanaApi,
) : TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {

        val confirmationStatus = solanaApi.checkStatus(txHash)
        return when (confirmationStatus) {
            "finalized" -> TransactionResult.Confirmed
            "confirmed", "processed" -> TransactionResult.Pending
            else -> TransactionResult.NotFound
        }
    }


}
