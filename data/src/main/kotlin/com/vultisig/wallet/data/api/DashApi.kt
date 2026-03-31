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

interface DashApi {
    suspend fun getAddressUtxos(address: String): List<UtxoInfo>
}

internal class DashApiImpl @Inject constructor(private val httpClient: HttpClient) : DashApi {

    companion object {
        private const val BASE_URL = "https://api.vultisig.com/dash/"
    }

    override suspend fun getAddressUtxos(address: String): List<UtxoInfo> {
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
            error("Dash RPC error: ${rpcResponse.error.message}")
        }

        return rpcResponse.result?.map { utxo ->
            UtxoInfo(hash = utxo.txid, amount = utxo.satoshis, index = utxo.outputIndex.toUInt())
        } ?: emptyList()
    }
}
