package com.vultisig.wallet.data.securityscanner


import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import javax.inject.Inject

class BlockaidRpcClient @Inject constructor(
    private val json: Json,
    private val httpClient: HttpClient,
): BlockaidRpcClientContract {
    // https://api.blockaid.io/v0/bitcoin/transaction-raw/scan
    override suspend fun scanBitcoinTransaction(serializedTransaction: String) {
        httpClient.post("") {
            contentType(ContentType.Application.Json)
        }
    }

    // https://api.blockaid.io/v0/evm/json-rpc/scan
    override suspend fun scanEVMTransaction(from: String, to: String, amount: String, data: String) {
        TODO("Not yet implemented")
    }

    // https://api.blockaid.io/v0/solana/address/scan
    override suspend fun scanSolanaTransaction(serializedMessage: String) {
        TODO("Not yet implemented")
    }

    // https://api.blockaid.io/v0/sui/transaction/scan
    override suspend fun scanSuiTransaction(serializedTransaction: String) {
        TODO("Not yet implemented")
    }
}