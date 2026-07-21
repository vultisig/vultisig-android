package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.BittensorApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import com.vultisig.wallet.data.utils.NetworkException
import io.ktor.http.HttpStatusCode
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import timber.log.Timber

internal class BittensorStatusProvider @Inject constructor(private val bittensorApi: BittensorApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            when (bittensorApi.getTxStatus(txHash)?.success) {
                true -> TransactionResult.Confirmed
                false -> TransactionResult.Failed("Transaction failed on Bittensor")
                null -> TransactionResult.Pending
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Bittensor status check failed for %s", txHash)
            // The TAO tx-status proxy rate limits aggressively (HTTP 429). Sleeping here before
            // returning Pending self-throttles the caller's fixed-interval poll so it stops
            // hammering the proxy while it is temporarily banned.
            if (e is NetworkException && e.httpStatusCode == HttpStatusCode.TooManyRequests.value) {
                delay(RATE_LIMIT_BACKOFF_MS)
            }
            TransactionResult.Pending
        }

    private companion object {
        const val RATE_LIMIT_BACKOFF_MS = 30_000L
    }
}
