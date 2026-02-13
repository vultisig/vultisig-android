package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject

class CardanoStatusProvider @Inject constructor(private val cardanoApi: CardanoApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val txStatus = cardanoApi.getTxStatus(txHash)

            when {

                txStatus == null -> {
                    TransactionResult.NotFound
                }

                txStatus.numConfirmations == null -> {
                    TransactionResult.Pending
                }

                txStatus.numConfirmations == 0 -> {
                    TransactionResult.Pending
                }

                else -> {
                    TransactionResult.Confirmed
                }
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}