package com.vultisig.wallet.data.api.models

import com.vultisig.wallet.data.utils.BigIntegerSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigInteger

@Serializable
data class BlockChairInfoJson(val data: Map<String, BlockChairInfo>)

@Serializable
data class BlockChairAddress(
    @SerialName("balance")
    val balance: Long,
    @SerialName("unspent_output_count")
    val unspentOutputCount: Int,
)

@Serializable
data class BlockChairUtxoInfo(
    @SerialName("transaction_hash")
    val transactionHash: String,
    @SerialName("index")
    val index: Int,
    @SerialName("value")
    val value: Long,
)

@Serializable
data class BlockChairInfo(
    @SerialName("address")
    val address: BlockChairAddress,
    @SerialName("utxo")
    val utxos: List<BlockChairUtxoInfo>,
)

@Serializable
data class SuggestedTransactionFeeDataJson(
    @SerialName("data")
    val data: SuggestedTransactionFeeJson
)

@Serializable
data class SuggestedTransactionFeeJson(
    @SerialName("suggested_transaction_fee_per_byte_sat")
    @Serializable(BigIntegerSerializer::class)
    val value: BigInteger
)

@Serializable
data class TransactionHashDataJson(
    @SerialName("data")
    val data: TransactionHashJson
)

@Serializable
data class TransactionHashJson(
    @SerialName("transaction_hash")
    val value: String
)



@Serializable
data class TransactionHashRequestBodyJson(
    @SerialName("data")
    val data: String
)


