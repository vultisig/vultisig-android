package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

internal class SolanaStatusProvider @Inject constructor(private val solanaApi: SolanaApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        // Transient HTTP / deserialization errors map to Pending so the poller retries.
        // CancellationException always propagates.
        return try {
            val confirmationStatus =
                solanaApi.checkStatus(txHash)?.result?.value?.firstOrNull()?.confirmationStatus
            when (confirmationStatus) {
                "finalized" -> TransactionResult.Confirmed
                "confirmed",
                "processed" -> TransactionResult.Pending
                null -> TransactionResult.Pending
                else -> TransactionResult.NotFound
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Solana tx status check failed for %s — treating as Pending", txHash)
            TransactionResult.Pending
        }
    }
}
