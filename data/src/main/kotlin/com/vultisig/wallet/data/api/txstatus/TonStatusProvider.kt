package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.call.body
import javax.inject.Inject

class TonStatusProvider @Inject constructor(
    private val tonApi: TonApi
) : TransactionStatusProvider {
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            var result = tonApi.getTsStatus(txHash)

            if (!result.error.isNullOrEmpty() || result.errorCode != null) {
                return TransactionResult.Failed(result.error ?: result.errorCode.toString())
            }

            if (result.inProgress == false) {
                return TransactionResult.Confirmed
            } else {
                TransactionResult.Pending
            }
        } catch (e: Exception) {
            if(e.message?.contains("entity not found", ignoreCase = true) ?: false) {
                return TransactionResult.Pending
            }
            TransactionResult.Failed(e.message.toString())
        }
    }
}