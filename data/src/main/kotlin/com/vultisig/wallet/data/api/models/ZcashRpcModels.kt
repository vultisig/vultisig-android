package com.vultisig.wallet.data.api.models

import kotlinx.serialization.Serializable

@Serializable
internal data class ZcashRpcRequest(
    val jsonrpc: String = "1.0",
    val id: String = "vultisig",
    val method: String,
    val params: List<String>,
)

/**
 * Request shape for the address-index RPCs (`getaddressbalance`, `getaddressutxos`), which take a
 * single object parameter rather than the plain string list [ZcashRpcRequest] carries.
 */
@Serializable
internal data class ZcashAddressRpcRequest(
    val jsonrpc: String = "1.0",
    val id: String = "vultisig",
    val method: String,
    val params: List<ZcashAddressParam>,
)

@Serializable internal data class ZcashAddressParam(val addresses: List<String>)

@Serializable internal data class ZcashRpcError(val code: Int = 0, val message: String = "")

@Serializable
internal data class ZcashAddressBalanceResponse(
    val result: ZcashAddressBalanceResult? = null,
    val error: ZcashRpcError? = null,
)

/** `balance` is the address's spendable transparent balance in zatoshi. */
@Serializable internal data class ZcashAddressBalanceResult(val balance: Long? = null)

@Serializable
internal data class ZcashAddressUtxosResponse(
    val result: List<ZcashAddressUtxo>? = null,
    val error: ZcashRpcError? = null,
)

@Serializable
internal data class ZcashAddressUtxo(val txid: String, val outputIndex: Int, val satoshis: Long)

@Serializable
internal data class ZcashBlockchainInfoResponse(val result: ZcashBlockchainInfoResult? = null)

@Serializable internal data class ZcashBlockchainInfoResult(val consensus: ZcashConsensus? = null)

@Serializable
internal data class ZcashConsensus(val chaintip: String? = null, val nextblock: String? = null)
