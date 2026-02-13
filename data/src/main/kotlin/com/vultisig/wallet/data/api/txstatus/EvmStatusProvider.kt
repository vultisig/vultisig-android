package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject

class EvmStatusProvider @Inject constructor(
    private val evmApiFactory: EvmApiFactory,
) : TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val rpcUrl = evmApiFactory.createEvmApi(chain)
            val evmJson = rpcUrl.getTxStatus(txHash)

            return if (evmJson == null) {
                TransactionResult.Pending
            } else {
                val result = evmJson.result
                val status = result.status
                when (status) {
                    "0x1" -> TransactionResult.Confirmed
                    "0x0" -> TransactionResult.Failed("Transaction reverted")
                    else -> TransactionResult.Pending
                }
            }

        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}