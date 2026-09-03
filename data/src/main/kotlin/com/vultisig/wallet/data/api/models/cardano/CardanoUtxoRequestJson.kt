package com.vultisig.wallet.data.api.models.cardano

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Koios `address_utxos` request.
 *
 * [extended] is what makes the response carry `asset_list`; without it Koios returns the lovelace
 * value alone and a token-bearing UTxO is indistinguishable from an ADA-only one. It deliberately
 * carries no default: the client serializes with `encodeDefaults = false`, which would drop a
 * property sitting on its declared default and silently ask for the plain form.
 */
@Serializable
data class CardanoUtxoRequestJson(
    @SerialName("_addresses") val addresses: List<String>,
    @SerialName("_extended") val extended: Boolean,
)
