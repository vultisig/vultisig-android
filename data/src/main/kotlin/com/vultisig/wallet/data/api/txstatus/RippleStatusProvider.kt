package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class RippleStatusProvider @Inject constructor(private val rippleApi: RippleApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val tx = rippleApi.getTsStatus(txHash)
            when {
                tx == null -> TransactionResult.Pending
                tx.result.status == "success" -> TransactionResult.Confirmed
                else -> TransactionResult.Failed(tx.result.status)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Ripple status check failed for %s", txHash)
            TransactionResult.Pending
        }
}
