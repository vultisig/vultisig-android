package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One native-asset holding from Koios `address_assets`, or one asset row on an extended
 * `address_utxos` entry.
 *
 * `asset_name` is hex, and empty for a policy's unnamed asset — together with [policyId] it forms
 * the `<policy_id>.<asset_name_hex>` id the curated catalog stores as a `Coin.contractAddress`.
 *
 * [decimals] is the Cardano token registry's value for the asset. Koios reports `0` for an asset
 * the registry does not list, so it cannot be read as "the registry says zero" — see
 * `CardanoTokenFinder` for why discovery takes it at face value anyway.
 */
@Serializable
data class CardanoAssetResponseJson(
    @SerialName("policy_id") val policyId: String? = null,
    @SerialName("asset_name") val assetName: String? = null,
    @SerialName("quantity") val quantity: String? = null,
    @SerialName("decimals") val decimals: Int? = null,
)
