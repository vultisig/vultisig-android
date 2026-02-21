package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import timber.log.Timber
import javax.inject.Inject


internal class SolanaStatusProvider @Inject constructor(
    private val solanaApi: SolanaApi,
) : TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        try {
            val confirmationStatus =
                solanaApi.checkStatus(txHash)?.result?.value?.firstOrNull()?.confirmationStatus
            return when (confirmationStatus) {
                "finalized" -> TransactionResult.Confirmed
                "confirmed", "processed" -> TransactionResult.Pending
                null -> TransactionResult.Pending
                else -> TransactionResult.NotFound
            }
        } catch (e: Exception) {
            Timber.tag("SolanaStatusProvider").e(e, "Failed to check status for $txHash")
            return TransactionResult.Failed(e.message.toString())
        }
    }
}
