package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.plugins.ClientRequestException
import javax.inject.Inject

class TronStatusProvider @Inject constructor(
    private val tronApi: TronApi,
) : TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val tx = tronApi.getTsStatus(chain, txHash)

            if (tx?.txId == null) {
                return TransactionResult.Pending
            }

            val contractRet = tx.ret?.firstOrNull()?.contractRet
                ?: return TransactionResult.Confirmed

            if (contractRet.equals("SUCCESS", ignoreCase = true)) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Failed(contractRet)
            }

        } catch (_: ClientRequestException) {
            TransactionResult.Pending
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}