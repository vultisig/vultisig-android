package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
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
            Timber.w(e, "Polkadot status check failed for %s", txHash)
            TransactionResult.Pending
        }
}
