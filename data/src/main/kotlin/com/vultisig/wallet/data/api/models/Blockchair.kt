package com.vultisig.wallet.data.api.models

import com.google.gson.annotations.SerializedName

data class BlockChairAddress(
    @SerializedName("balance")
    val balance: String,
    @SerializedName("unspent_output_count")
    val unspentOutputCount: Int,
)

data class BlockChairUtxoInfo(
    @SerializedName("transaction_hash")
    val transactionHash: String,
    @SerializedName("index")
    val index: Int,
    @SerializedName("value")
    val value: Long,
)

data class BlockChairInfo(
    @SerializedName("address")
    val address: BlockChairAddress,
    @SerializedName("utxo")
    val utxos: List<BlockChairUtxoInfo>,
)