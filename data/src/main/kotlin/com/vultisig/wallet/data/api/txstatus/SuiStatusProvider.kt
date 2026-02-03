package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject

class SuiStatusProvider @Inject constructor(
    private val suiApi: SuiApi,
) : TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {

        val txResponse = suiApi.checkStatus(txHash)

        return when {

            txResponse == null -> {
                TransactionResult.NotFound
            }

            txResponse.checkpoint == null -> {
                TransactionResult.Pending
            }

            txResponse.effects?.status != null -> {
                when (txResponse.effects.status.status) {
                    "success" -> TransactionResult.Confirmed
                    "failure" -> TransactionResult.Failed(
                        txResponse.effects.status.error ?: "Transaction execution failed"
                    )

                    else -> TransactionResult.Pending
                }
            }

            else -> {
                TransactionResult.Confirmed
            }
        }
    }
}