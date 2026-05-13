package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class SuiStatusProvider @Inject constructor(private val suiApi: SuiApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        val txResponse =
            try {
                suiApi.checkStatus(txHash)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Sui status check failed for %s", txHash)
                return TransactionResult.Pending
            }

        if (txResponse == null) return TransactionResult.NotFound
        if (txResponse.checkpoint == null) return TransactionResult.Pending

        val effectsStatus = txResponse.effects?.status ?: return TransactionResult.Confirmed
        return when (effectsStatus.status) {
            "success" -> TransactionResult.Confirmed
            "failure" ->
                TransactionResult.Failed(effectsStatus.error ?: "Transaction execution failed")
            else -> TransactionResult.Pending
        }
    }
}
