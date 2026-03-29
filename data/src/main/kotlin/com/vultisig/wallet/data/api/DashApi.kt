package com.vultisig.wallet.data.api

import com.vultisig.wallet.data.api.models.DashAddressParam
import com.vultisig.wallet.data.api.models.DashRpcRequest
import com.vultisig.wallet.data.api.models.DashRpcResponse
import com.vultisig.wallet.data.models.payload.UtxoInfo
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

interface DashApi {
    suspend fun getAddressUtxos(address: String): List<UtxoInfo>
}

internal class DashApiImpl @Inject constructor(private val httpClient: HttpClient) : DashApi {

    companion object {
        private const val BASE_URL = "https://api.vultisig.com/dash/"
    }

    override suspend fun getAddressUtxos(address: String): List<UtxoInfo> {
        try {
            val rpcResponse =
                httpClient
                    .post(BASE_URL) {
                        setBody(
                            DashRpcRequest(
                                method = "getaddressutxos",
                                params = listOf(DashAddressParam(addresses = listOf(address))),
                            )
                        )
                    }
                    .body<DashRpcResponse>()

            if (rpcResponse.error != null) {
                Timber.e("Dash RPC error: %s", rpcResponse.error)
                return emptyList()
            }

            return rpcResponse.result?.map { utxo ->
                UtxoInfo(
                    hash = utxo.txid,
                    amount = utxo.satoshis,
                    index = utxo.outputIndex.toUInt(),
                )
            } ?: emptyList()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e, "Failed to get Dash UTXOs via RPC")
            return emptyList()
        }
    }
}
