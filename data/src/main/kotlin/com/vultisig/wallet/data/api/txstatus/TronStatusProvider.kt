package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.TronApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.plugins.ClientRequestException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class TronStatusProvider @Inject constructor(private val tronApi: TronApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val tx = tronApi.getTsStatus(chain, txHash)
            val contractRet = tx?.ret?.firstOrNull()?.contractRet
            when {
                tx?.txId == null -> TransactionResult.Pending
                contractRet == null -> TransactionResult.Confirmed
                contractRet.equals("SUCCESS", ignoreCase = true) -> TransactionResult.Confirmed
                else -> TransactionResult.Failed(contractRet)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ClientRequestException) {
            Timber.w(e, "Tron status check got client error for %s", txHash)
            TransactionResult.Pending
        } catch (e: Exception) {
            Timber.w(e, "Tron status check failed for %s", txHash)
            TransactionResult.Pending
        }
}
