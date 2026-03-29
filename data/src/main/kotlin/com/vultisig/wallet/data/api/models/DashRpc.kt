package com.vultisig.wallet.data.api.models

import kotlinx.serialization.Serializable

@Serializable
internal data class DashRpcRequest(
    val jsonrpc: String = "1.0",
    val id: String = "vultisig",
    val method: String,
    val params: List<DashAddressParam>,
)

@Serializable internal data class DashAddressParam(val addresses: List<String>)

@Serializable
internal data class DashRpcResponse(
    val result: List<DashAddressUtxo>?,
    val error: DashRpcError?,
    val id: String,
)

@Serializable internal data class DashRpcError(val code: Int, val message: String)

@Serializable
internal data class DashAddressUtxo(
    val address: String,
    val txid: String,
    val outputIndex: Int,
    val script: String,
    val satoshis: Long,
    val height: Long,
)
