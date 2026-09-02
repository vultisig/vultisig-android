package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One native-asset holding from Koios `address_assets`.
 *
 * `asset_name` is hex, and empty for a policy's unnamed asset — together with [policyId] it forms
 * the `<policy_id>.<asset_name_hex>` id the curated catalog stores as a `Coin.contractAddress`.
 */
@Serializable
data class CardanoAssetResponseJson(
    @SerialName("policy_id") val policyId: String? = null,
    @SerialName("asset_name") val assetName: String? = null,
    @SerialName("quantity") val quantity: String? = null,
)
