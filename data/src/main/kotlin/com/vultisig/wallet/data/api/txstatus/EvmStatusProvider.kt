package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

class EvmStatusProvider @Inject constructor(private val evmApiFactory: EvmApiFactory) :
    TransactionStatusProvider {

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        // Distinguish three failure modes that used to be collapsed into `NotFound`:
        //   1. Transient network / 5xx / RPC error  → return Pending so the poller retries
        //      and the local row is not persisted as FAILED on the first blip.
        //   2. The RPC call returns a result object with `status = "0x0"` → the chain has
        //      a definitive on-chain revert → return Failed.
        //   3. The RPC returns null (tx not in the canonical chain yet) → return Pending.
        //
        // CancellationException must always propagate so structured concurrency is preserved.
        return try {
            val api = evmApiFactory.createEvmApi(chain)
            val evmJson = api.getTxStatus(txHash)

            if (evmJson == null) {
                TransactionResult.Pending
            } else {
                when (evmJson.result.status) {
                    "0x1" -> TransactionResult.Confirmed
                    "0x0" -> TransactionResult.Failed("Transaction reverted")
                    else -> TransactionResult.Pending
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Treat any unexpected HTTP / deserialization / network failure as transient.
            // The caller (PollingTxStatusUseCase / RefreshPendingTransactionsUseCase) decides
            // when to give up after repeated transient failures; a single error must not
            // poison the database with a bogus FAILED status.
            Timber.w(
                e,
                "EVM tx status check failed for %s on %s — treating as Pending",
                txHash,
                chain,
            )
            TransactionResult.Pending
        }
    }
}
