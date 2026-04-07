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

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult =
        try {
            val finality = tonApi.getTsStatus(txHash).transactions.firstOrNull()?.finality
            if (finality?.lowercase()?.contains("unknown") == true) TransactionResult.Confirmed
            else TransactionResult.Pending
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "TON status check failed for %s", txHash)
            TransactionResult.Pending
        }
}
