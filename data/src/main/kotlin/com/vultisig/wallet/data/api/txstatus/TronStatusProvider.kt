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

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        // Transient HTTP / deserialization errors map to Pending so the poller retries.
        // CancellationException always propagates.
        return try {
            val tx = tronApi.getTsStatus(chain, txHash)

            if (tx?.txId == null) return TransactionResult.Pending

            val contractRet =
                tx.ret?.firstOrNull()?.contractRet ?: return TransactionResult.Confirmed

            if (contractRet.equals("SUCCESS", ignoreCase = true)) {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Failed(contractRet)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: ClientRequestException) {
            TransactionResult.Pending
        } catch (e: Exception) {
            Timber.w(e, "Tron tx status check failed for %s — treating as Pending", txHash)
            TransactionResult.Pending
        }
    }
}
