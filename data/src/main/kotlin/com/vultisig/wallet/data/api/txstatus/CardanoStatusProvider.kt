package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.CardanoApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class CardanoStatusProvider @Inject constructor(private val cardanoApi: CardanoApi) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        // Transient HTTP / deserialization errors map to Pending so the poller retries.
        // CancellationException always propagates.
        return try {
            val txStatus = cardanoApi.getTxStatus(txHash)

            when {
                txStatus == null -> TransactionResult.NotFound
                txStatus.numConfirmations == null -> TransactionResult.Pending
                txStatus.numConfirmations == 0 -> TransactionResult.Pending
                else -> TransactionResult.Confirmed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Cardano tx status check failed for %s — treating as Pending", txHash)
            TransactionResult.Pending
        }
    }
}
