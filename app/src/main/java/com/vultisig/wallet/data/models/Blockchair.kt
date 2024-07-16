package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName

internal data class BlockchairAddress(
    @SerializedName("balance")
    val balance: String,
    @SerializedName("unspent_output_count")
    val unspentOutputCount: Int,
)

internal data class BlockchairUtxoInfo(
    @SerializedName("transaction_hash") val transactionHash: String,
    val index: Int,
    @SerializedName("value")
    val value: Long,
)

internal data class BlockchairInfo(
    @SerializedName("address")
    val address: BlockchairAddress,
    @SerializedName("utxo")
    val utxos: List<BlockchairUtxoInfo>,
)