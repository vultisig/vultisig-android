package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject

class CosmosStatusProvider @Inject constructor(
    private val cosmosApiFactory: CosmosApiFactory,
) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        try {
            val txResponse = cosmosApiFactory.createCosmosApi(chain).getTxStatus(txHash)
                ?: return TransactionResult.NotFound

            return if (txResponse.txResponse?.height?.toIntOrNull() != null &&
                txResponse.txResponse.height.toInt() > 0
            ) {
                if (txResponse.txResponse.code == 0) {
                    TransactionResult.Confirmed
                } else {
                    TransactionResult.Failed(
                        txResponse.txResponse.rawLog
                            ?: "Transaction failed with code ${txResponse.txResponse.code}"
                    )
                }
            } else {
                TransactionResult.Pending
            }
        } catch (e: Exception) {
            if(e.message?.contains("tx not found") == true){
                return TransactionResult.Pending
            }
            return TransactionResult.Failed(e.message.toString())
        }
    }

}