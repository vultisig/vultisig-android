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
            var resp = tonApi.getTsStatus(txHash)

            if (resp.transactions.firstOrNull()?.finality != null && resp.transactions.firstOrNull()?.finality?.lowercase()
                    ?.contains("unknown") == true
            ) {
                return TransactionResult.Confirmed
            } else {
                TransactionResult.Pending
            }
        } catch (e: Exception) {
            TransactionResult.Failed(e.message.toString())
        }
    }
}