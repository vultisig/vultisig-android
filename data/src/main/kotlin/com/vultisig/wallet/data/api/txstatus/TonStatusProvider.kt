package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.chains.TonApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.call.body
import javax.inject.Inject

class TonStatusProvider @Inject constructor(
    private val tonApi: TonApi
) : TransactionStatusProvider {
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            var resp = tonApi.getTsStatus(txHash)

            val tx = resp.transactions.firstOrNull { it.hash == txHash } ?: return TransactionResult.NotFound

            val desc = tx.description

            if (desc?.aborted == true) return TransactionResult.Failed("aborted")
            if (desc?.destroyed == true) return TransactionResult.Failed("destroyed")

            val computeSuccess = desc?.computePh?.success
            val computeExit = desc?.computePh?.exitCode
            val actionSuccess = desc?.action?.success
            val actionCode = desc?.action?.resultCode

            if (computeSuccess == true && actionSuccess == true && (computeExit == null || computeExit == 0)) {
                return TransactionResult.Confirmed
            }

            if (computeSuccess == false || actionSuccess == false || (computeExit != null && computeExit != 0) || (actionCode != null && actionCode != 0)) {
                val parts = mutableListOf<String>()
                if (computeSuccess == false) parts.add("compute failed")
                if (computeExit != null && computeExit != 0) parts.add("compute exit=$computeExit")
                if (actionSuccess == false) parts.add("action failed")
                if (actionCode != null && actionCode != 0) parts.add("action code=$actionCode")
                return TransactionResult.Failed(parts.joinToString("; "))
            }

            // otherwise treat as in-progress / not finalized
            return TransactionResult.Pending
        } catch (e: Exception) {
            if(e.message?.contains("entity not found", ignoreCase = true) ?: false) {
                return TransactionResult.Pending
            }
            TransactionResult.Failed(e.message.toString())
        }
    }
}