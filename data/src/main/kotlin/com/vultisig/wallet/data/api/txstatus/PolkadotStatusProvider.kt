package com.vultisig.wallet.data.api.txstatus

import com.vultisig.wallet.data.api.PolkadotApi
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
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import javax.inject.Inject

class PolkadotStatusProvider @Inject constructor(
    private val polkadotApi: PolkadotApi,
) : TransactionStatusProvider {

    private val apiUrl = "https://polkadot.api.subscan.io/api/scan/extrinsic"

    override suspend fun checkStatus(txHash: String, chain: Chain): TransactionResult {
        return try {
            val tx = polkadotApi.getTsStatus(txHash)
            if (tx == null) {
                return TransactionResult.NotFound
            }
            if (tx.message.lowercase() == "success") {
                return TransactionResult.Confirmed
            } else {
                return TransactionResult.Failed(tx.data?.polkadotErrorData?.value ?: tx.message)
            }
        } catch (e: Exception) {
            TransactionResult.Failed(e.message.toString())
        }
    }
}