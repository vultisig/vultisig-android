package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class TonStatusProvider @Inject constructor(private val tonApi: TonApi) :
    TransactionStatusProvider {
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        // Transient HTTP / deserialization errors map to Pending so the poller retries.
        // CancellationException always propagates.
        return try {
            val resp = tonApi.getTsStatus(txHash)

            if (
                resp.transactions.firstOrNull()?.finality != null &&
                    resp.transactions.firstOrNull()?.finality?.lowercase()?.contains("unknown") ==
                        true
            ) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Pending
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "TON tx status check failed for %s — treating as Pending", txHash)
            TransactionResult.Pending
        }
    }
}
