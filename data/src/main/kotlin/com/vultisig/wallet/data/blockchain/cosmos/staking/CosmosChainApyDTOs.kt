package com.vultisig.wallet.data.blockchain.cosmos.staking

import java.math.BigDecimal
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire shapes + value types for the 4 LCD endpoints that feed the on-chain APY computation:
 *
 *     /cosmos/mint/v1beta1/inflation         → CosmosMintInflationResponse
 *     /cosmos/staking/v1beta1/pool           → CosmosStakingPoolResponse
 *     /cosmos/bank/v1beta1/supply/by_denom   → CosmosBankSupplyResponse
 *     /cosmos/distribution/v1beta1/params    → CosmosDistributionParamsResponse
 *
 * Formula (mirrors Windows `core/ui/chain/cosmos/apr/` and iOS `CosmosStakingAPYResolver.swift`):
 *
 *     apy = (1 − communityTax) × (inflation / bondedRatio) × (1 − commission)
 *
 * with `bondedRatio = bondedTokens / totalSupply`. Inputs clamp to `[0, 1]`; zero inflation or zero
 * bonded ratio collapses APY to `null` so the row hides per the Windows / iOS behavior.
 */

/**
 * Aggregated, denom-aware APY inputs for a chain. The per-validator APY multiplier is applied
 * downstream — this is the chain-level constant cached for 5 minutes.
 */
data class CosmosChainApyData(
    /**
     * Annual inflation rate, clamped to `[0, 1]`. Zero when the chain's mint module is disabled.
     */
    val inflation: BigDecimal,
    /** Bonded tokens / total supply, clamped to `[0, 1]`. */
    val bondedRatio: BigDecimal,
    /** Community-pool skim taken before per-validator commission. */
    val communityTax: BigDecimal,
)

@Serializable data class CosmosMintInflationResponse(val inflation: String)

@Serializable
data class CosmosStakingPoolResponse(val pool: Pool) {
    @Serializable
    data class Pool(
        @SerialName("not_bonded_tokens") val notBondedTokens: String,
        @SerialName("bonded_tokens") val bondedTokens: String,
    )
}

@Serializable data class CosmosBankSupplyResponse(val amount: CosmosStakingCoin)

@Serializable
data class CosmosDistributionParamsResponse(val params: Params) {
    @Serializable
    data class Params(
        /**
         * `cosmos.Dec` string. LUNC has historically returned 0% community tax; LUNA varies with
         * gov proposals.
         */
        @SerialName("community_tax") val communityTax: String
    )
}
