package com.vultisig.wallet.data.securityscanner.blockaid

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Wire models for the Blockaid simulation endpoints.
 *
 * EVM uses `/evm/json-rpc/scan` with a JSON-RPC `eth_sendTransaction` payload. Solana reuses the
 * existing `/solana/message/scan` endpoint but adds the `simulation` option to populate
 * `result.simulation` alongside the validation data already returned today.
 *
 * The [BlockaidSimulationOptions] object mirrors the option strings documented by Blockaid; callers
 * MUST request `validation` together with `simulation` to keep the security badge wiring intact.
 */
object BlockaidSimulationOptions {
    const val SIMULATION = "simulation"
    const val VALIDATION = "validation"
}

// ---------- EVM simulation request -------------------------------------------------

/**
 * EVM simulate request payload — unique to the simulation endpoint.
 *
 * Blockaid's simulate route accepts a JSON-RPC envelope (`eth_sendTransaction`) rather than the
 * flat `from/to/data/value` shape used by the validation-only scan endpoint, hence the dedicated
 * request type.
 */
@Serializable
data class BlockaidEvmSimulateRequestJson(
    @SerialName("data") val data: DataJson,
    @SerialName("chain") val chain: String,
    @SerialName("metadata") val metadata: MetadataJson,
    @SerialName("options") val options: List<String>,
) {
    @Serializable data class MetadataJson(@SerialName("domain") val domain: String)

    @Serializable
    data class DataJson(
        @SerialName("method") val method: String,
        @SerialName("params") val params: List<ParamsJson>,
    ) {
        @Serializable
        data class ParamsJson(
            @SerialName("from") val from: String,
            @SerialName("to") val to: String,
            @SerialName("value") val value: String,
            @SerialName("data") val data: String,
        )
    }

    companion object {
        const val METHOD_ETH_SEND_TRANSACTION = "eth_sendTransaction"
    }
}

// ---------- EVM simulation response ------------------------------------------------

/**
 * Top-level response from `/evm/json-rpc/scan`.
 *
 * The `simulation` block carries the balance-change diffs that drive the dApp hero. The
 * `validation` block reuses the same shape as the existing validation-only endpoint so both code
 * paths can share decoding logic.
 */
@Serializable
data class BlockaidEvmSimulationResponseJson(
    @SerialName("simulation") val simulation: BlockaidEvmSimulationJson? = null,
    @SerialName("validation")
    val validation: BlockaidTransactionScanResponseJson.BlockaidValidationJson? = null,
    @SerialName("error") val error: String? = null,
)

@Serializable
data class BlockaidEvmSimulationJson(
    @SerialName("status") val status: String? = null,
    @SerialName("account_summary") val accountSummary: AccountSummary? = null,
) {
    @Serializable
    data class AccountSummary(@SerialName("assets_diffs") val assetsDiffs: List<AssetDiff>? = null)

    @Serializable
    data class AssetDiff(
        @SerialName("asset") val asset: Asset,
        @SerialName("asset_type") val assetType: String? = null,
        @SerialName("in") val incoming: List<BalanceChange>? = null,
        @SerialName("out") val outgoing: List<BalanceChange>? = null,
    )

    @Serializable
    data class Asset(
        @SerialName("type") val type: String? = null,
        @SerialName("decimals") val decimals: Int? = null,
        @SerialName("address") val address: String? = null,
        @SerialName("logo_url") val logoUrl: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("symbol") val symbol: String? = null,
    )

    @Serializable data class BalanceChange(@SerialName("raw_value") val rawValue: String? = null)
}

// ---------- Solana simulation response ---------------------------------------------

/**
 * Top-level response from `/solana/message/scan` when `simulation` is requested.
 *
 * Solana wraps the simulation + validation pair under a `result` object, unlike the EVM shape which
 * exposes them at the root.
 */
@Serializable
data class BlockaidSolanaSimulationResponseJson(
    @SerialName("result") val result: BlockaidSolanaSimulationResultJson? = null,
    @SerialName("status") val status: String? = null,
    @SerialName("error") val error: String? = null,
) {
    @Serializable
    data class BlockaidSolanaSimulationResultJson(
        @SerialName("simulation") val simulation: BlockaidSolanaSimulationJson? = null,
        @SerialName("validation")
        val validation:
            BlockaidTransactionScanResponseJson.BlockaidSolanaResultJson.BlockaidSolanaValidationJson? =
            null,
    )
}

/**
 * Solana simulation diffs.
 *
 * Note the divergences from EVM that the parser has to absorb:
 * - field name is `account_assets_diff` (singular "diff") not `assets_diffs`
 * - `in` / `out` are single objects, not arrays
 * - `raw_value` is sometimes a JSON number — see [SolanaBalanceChange]
 */
@Serializable
data class BlockaidSolanaSimulationJson(
    @SerialName("account_summary") val accountSummary: AccountSummary? = null
) {
    @Serializable
    data class AccountSummary(
        @SerialName("account_assets_diff") val accountAssetsDiff: List<AccountAssetDiff>? = null
    )

    @Serializable
    data class AccountAssetDiff(
        @SerialName("asset") val asset: Asset,
        @SerialName("asset_type") val assetType: String? = null,
        @SerialName("in") val incoming: SolanaBalanceChange? = null,
        @SerialName("out") val outgoing: SolanaBalanceChange? = null,
    )

    @Serializable
    data class Asset(
        @SerialName("type") val type: String? = null,
        @SerialName("name") val name: String? = null,
        @SerialName("symbol") val symbol: String? = null,
        @SerialName("address") val address: String? = null,
        @SerialName("decimals") val decimals: Int? = null,
        @SerialName("logo") val logo: String? = null,
    )

    /**
     * Solana encodes `raw_value` as a JSON number while EVM quotes it as a string. Blockaid is
     * inconsistent across responses, so the JSON element is decoded loosely here and normalised to
     * a [String] by the parser.
     */
    @Serializable
    data class SolanaBalanceChange(@SerialName("raw_value") val rawValue: JsonElement? = null)
}
