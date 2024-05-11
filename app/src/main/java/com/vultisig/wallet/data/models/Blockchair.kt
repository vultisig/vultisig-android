package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName

data class BlockchairAddress(
    val balance: ULong,
    @SerializedName("unspent_output_count") val unspentOutputCount: Int,
)

data class BlockchairUtxoInfo(
    @SerializedName("transaction_hash") val transactionHash: String,
    val index: Int,
    val value: ULong,
)

data class BlockchairInfo(
    val address: BlockchairAddress,
    val utxos: List<BlockchairUtxoInfo>,
)