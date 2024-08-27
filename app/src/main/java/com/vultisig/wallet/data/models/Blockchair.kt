package com.vultisig.wallet.data.models

import com.google.gson.annotations.SerializedName

internal data class BlockChairAddress(
    @SerializedName("balance")
    val balance: String,
    @SerializedName("unspent_output_count")
    val unspentOutputCount: Int,
)

internal data class BlockChairUtxoInfo(
    @SerializedName("transaction_hash")
    val transactionHash: String,
    @SerializedName("index")
    val index: Int,
    @SerializedName("value")
    val value: Long,
)

internal data class BlockChairInfo(
    @SerializedName("address")
    val address: BlockChairAddress,
    @SerializedName("utxo")
    val utxos: List<BlockChairUtxoInfo>,
)