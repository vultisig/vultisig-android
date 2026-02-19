package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TransactionStatusProvider
import io.ktor.client.HttpClient
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class RippleStatusProvider @Inject constructor(
    private val rippleApi: RippleApi,
) : TransactionStatusProvider {
    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val tx = rippleApi.getTsStatus(txHash)
            if (tx == null) {
                return TransactionResult.Pending
            }
            if (tx.result.status == "success") {
                TransactionResult.Confirmed
            } else {
                TransactionResult.Failed(tx.result.status)
            }
        } catch (_: Exception) {
            TransactionResult.NotFound
        }
    }
}