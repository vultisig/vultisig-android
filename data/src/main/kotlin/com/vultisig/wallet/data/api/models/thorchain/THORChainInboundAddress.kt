package com.vultisig.wallet.data.api.models.thorchain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Inbound vault address and trading-halt status for a given chain from the THORChain API. */
@Serializable
data class THORChainInboundAddress(
    @SerialName("chain") val chain: String,
    @SerialName("address") val address: String,
    @SerialName("halted") val halted: Boolean,
    @SerialName("global_trading_paused") val globalTradingPaused: Boolean,
    @SerialName("chain_trading_paused") val chainTradingPaused: Boolean,
    @SerialName("chain_lp_actions_paused") val chainLPActionsPaused: Boolean,
    @SerialName("gas_rate") val gasRate: String,
    @SerialName("gas_rate_units") val gasRateUnits: String,
)
