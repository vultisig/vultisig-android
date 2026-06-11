package com.vultisig.wallet.data.api.models

import kotlinx.serialization.Serializable

@Serializable
internal data class ZcashRpcRequest(
    val jsonrpc: String = "1.0",
    val id: String = "vultisig",
    val method: String,
    val params: List<String>,
)

@Serializable
internal data class ZcashBlockchainInfoResponse(val result: ZcashBlockchainInfoResult? = null)

@Serializable internal data class ZcashBlockchainInfoResult(val consensus: ZcashConsensus? = null)

@Serializable
internal data class ZcashConsensus(val chaintip: String? = null, val nextblock: String? = null)
