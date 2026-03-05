package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject

class RippleStatusProvider @Inject constructor(private val rippleApi: RippleApi) :
    TransactionStatusProvider {
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val tx = rippleApi.getTsStatus(txHash)
            if (tx == null) {
                return TransactionResult.Pending
            }
            if (tx.result.status == "success") {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Failed(tx.result.status)
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}
