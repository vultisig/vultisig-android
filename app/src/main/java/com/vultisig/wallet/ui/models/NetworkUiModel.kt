package com.vultisig.wallet.ui.models

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.logo

data class NetworkUiModel(
    val chain: Chain,
    val logo: ImageModel,
    val title: String,
    val value: String? = null,
)

fun Chain.toNetworkUiModel(consolidateEvm: Boolean, value: String? = null) =
    if (consolidateEvm && standard == TokenStandard.EVM) {
        evmNetworkUiModel
    } else
        toNetworkUiModel(value)

fun Chain.toNetworkUiModel(value: String? = null) = NetworkUiModel(
    chain = this,
    logo = logo,
    title = raw,
    value = value,
)

fun Iterable<Chain>.consolidateEvm(chainBalances: Map<Chain, String>) = groupBy { it.standard }
    .flatMap { (type, items) ->
        if (type == TokenStandard.EVM) {
            listOf(evmNetworkUiModel)
        } else {
            items.map { chain ->
                chain.toNetworkUiModel(chainBalances[chain] ?: "")
            }
        }
    }

val evmNetworkUiModel = NetworkUiModel(
    chain = Chain.Ethereum,
    logo = Chain.Ethereum.logo,
    title = "EVM",
    value = "",
)