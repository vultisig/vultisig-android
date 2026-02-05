package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject

class PolkadotStatusProvider @Inject constructor(
    private val polkadotApi: PolkadotApi,
) : TransactionStatusProvider {
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val tx = polkadotApi.getTxStatus(txHash)
            if (tx == null) {
                return TransactionResult.NotFound
            }
            if (tx.message.lowercase() == "success") {
                return TransactionResult.Confirmed
            } else {
                return TransactionResult.Failed(tx.data?.polkadotErrorData?.value ?: tx.message)
            }
        } catch (e: Exception) {
            TransactionResult.Failed(e.message.toString())
        }
    }
}