package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.NearApi
import com.vultisig.wallet.data.api.NearRpcException
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import timber.log.Timber

/**
 * NEAR has no hash-only lookup: the node is asked by `(tx_hash, sender_account_id)` because the
 * lookup is sharded by sender. `FINAL` is the only status reported as settled; an `INCLUDED` or
 * optimistic outcome can still revert.
 */
internal class NearStatusProvider @Inject constructor(private val nearApi: NearApi) {

    suspend fun checkStatus(txHash: String, senderAccountId: String?): TransactionResult {
        if (senderAccountId.isNullOrEmpty()) {
            Timber.e("NEAR status for %s requested without the sender account", txHash)
            return TransactionResult.Pending
        }
        return try {
            val outcome = nearApi.getTransactionOutcome(txHash, senderAccountId)
            if (outcome.returnedHash != null && outcome.returnedHash != txHash) {
                error("NEAR status for $txHash returned the outcome of ${outcome.returnedHash}")
            }
            if (outcome.finalExecutionStatus != FINAL) return TransactionResult.Pending
            // A FINAL outcome always names its transaction; one that does not cannot be bound.
            checkNotNull(outcome.returnedHash) {
                "NEAR status for $txHash reported a final outcome without the transaction hash"
            }
            when (val status = outcome.status) {
                is JsonPrimitive if status.content in SUCCESS -> TransactionResult.Confirmed
                is JsonObject if SUCCESS.any { it in status } -> TransactionResult.Confirmed
                is JsonObject if FAILURE in status ->
                    TransactionResult.Failed("NEAR transaction failed: ${status[FAILURE]}")
                else -> error("NEAR status for $txHash carries an unrecognized execution status")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: NearRpcException) {
            when (e.name) {
                UNKNOWN_TRANSACTION -> TransactionResult.NotFound
                else -> {
                    // A node-side wait timeout or transient error leaves the state unknown.
                    Timber.w(e, "NEAR status check failed for %s", txHash)
                    TransactionResult.Pending
                }
            }
        } catch (e: IllegalStateException) {
            // An answer that cannot be bound to this hash is never reported as settled.
            Timber.e(e, "NEAR status for %s could not be read", txHash)
            TransactionResult.Pending
        }
    }

    private companion object {
        const val FINAL = "FINAL"
        const val FAILURE = "Failure"
        const val UNKNOWN_TRANSACTION = "UNKNOWN_TRANSACTION"
        val SUCCESS = setOf("SuccessValue", "SuccessReceiptId")
    }
}
