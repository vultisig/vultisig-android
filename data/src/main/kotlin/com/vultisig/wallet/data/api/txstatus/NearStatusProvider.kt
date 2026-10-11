package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.NearApi
import com.vultisig.wallet.data.api.NearRpcException
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.json.JsonElement
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
        val outcome =
            try {
                nearApi.getTransactionOutcome(txHash, senderAccountId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: NearRpcException) {
                if (e.name == UNKNOWN_TRANSACTION) return TransactionResult.NotFound
                // A node-side wait timeout or transient error leaves the state unknown.
                Timber.w(e, "NEAR status check failed for %s", txHash)
                return TransactionResult.Pending
            } catch (e: Exception) {
                // A transport failure leaves the state unknown, never settled: the transaction may
                // have landed.
                Timber.e(e, "NEAR status for %s could not be read", txHash)
                return TransactionResult.Pending
            }
        val returnedHash = outcome.returnedHash
        return when {
            returnedHash != null && returnedHash != txHash ->
                unbound(txHash, "returned the outcome of another transaction")
            outcome.finalExecutionStatus != FINAL -> TransactionResult.Pending
            // A FINAL outcome always names its transaction; one that does not cannot be bound.
            returnedHash == null -> unbound(txHash, "reported a final outcome without its hash")
            else -> finalResult(txHash, outcome.status)
        }
    }

    private fun finalResult(txHash: String, status: JsonElement?): TransactionResult =
        when (status) {
            is JsonPrimitive if status.content in SUCCESS -> TransactionResult.Confirmed
            is JsonObject if SUCCESS.any { it in status } -> TransactionResult.Confirmed
            is JsonObject if FAILURE in status ->
                TransactionResult.Failed("NEAR transaction failed: ${status[FAILURE]}")
            else -> unbound(txHash, "carries an unrecognized execution status")
        }

    /** An answer that cannot be bound to [txHash] is never reported as settled. */
    private fun unbound(txHash: String, reason: String): TransactionResult {
        Timber.e("NEAR status for %s %s", txHash, reason)
        return TransactionResult.Pending
    }

    private companion object {
        const val FINAL = "FINAL"
        const val FAILURE = "Failure"
        const val UNKNOWN_TRANSACTION = "UNKNOWN_TRANSACTION"
        val SUCCESS = setOf("SuccessValue", "SuccessReceiptId")
    }
}
