package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ThorChainStatusResponse(val result: Result) {
    @Serializable data class Result(@SerialName("node_info") val nodeInfo: NodeInfo)

    @Serializable data class NodeInfo(val network: String)
}
