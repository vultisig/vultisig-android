package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import com.vultisig.wallet.data.utils.NetworkException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class PolkadotStatusProvider @Inject constructor(private val polkadotApi: PolkadotApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val tx = polkadotApi.getTxStatus(txHash)
            when {
                tx == null -> TransactionResult.NotFound
                tx.message.equals("success", ignoreCase = true) -> TransactionResult.Confirmed
                else -> TransactionResult.Failed(tx.data?.polkadotErrorData?.value ?: tx.message)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isExplorerUnauthorized()) {
                // Subscan's free tier now rejects unauthenticated traffic, so the lookup will
                // never succeed. Surface a terminal result instead of polling for the full
                // timeout window and hammering an endpoint that will keep refusing us.
                Timber.w("Polkadot explorer unauthorized; ending status check for %s", txHash)
                TransactionResult.TimedOut
            } else {
                Timber.w(e, "Polkadot status check failed for %s", txHash)
                TransactionResult.Pending
            }
        }

    private fun Throwable.isExplorerUnauthorized(): Boolean {
        val exception = this as? NetworkException ?: return false
        if (exception.httpStatusCode != 400) return false
        return SUBSCAN_AUTH_WALL_PHRASES.any { exception.message.contains(it, ignoreCase = true) }
    }

    private companion object {
        // Subscan returns HTTP 400 with body `{"code": 403, "message": "Subscan API strictly
        // requires an API key. Unauthenticated access is disabled."}` when the request omits
        // the API key. Match both phrases — looser matching could classify unrelated
        // bad-request responses as terminal and stop polling early.
        val SUBSCAN_AUTH_WALL_PHRASES =
            listOf("requires an API key", "Unauthenticated access is disabled")
    }
}
