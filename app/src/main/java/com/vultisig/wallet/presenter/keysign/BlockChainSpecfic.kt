package com.vultisig.wallet.presenter.keysign

import com.google.gson.annotations.SerializedName
import java.math.BigInteger

internal sealed class BlockChainSpecific {
    data class UTXO(
        @SerializedName("byteFee")
        val byteFee: BigInteger,
        @SerializedName("sendMaxAmount")
        val sendMaxAmount: Boolean
    ) : BlockChainSpecific()

    data class Ethereum(
        @SerializedName("maxFeePerGasWei")
        val maxFeePerGasWei: BigInteger,
        @SerializedName("priorityFeeWei")
        val priorityFeeWei: BigInteger,
        @SerializedName("nonce")
        val nonce: BigInteger,
        @SerializedName("gasLimit")
        val gasLimit: BigInteger,
    ) : BlockChainSpecific()

    data class THORChain(
        @SerializedName("accountNumber")
        val accountNumber: BigInteger,
        @SerializedName("sequence")
        val sequence: BigInteger,
        @SerializedName("fee")
        val fee: BigInteger,
        val isDeposit: Boolean,
    ) : BlockChainSpecific()

    data class MayaChain(
        @SerializedName("accountNumber")
        val accountNumber: BigInteger,
        @SerializedName("sequence")
        val sequence: BigInteger,
        val isDeposit: Boolean,
    ) : BlockChainSpecific()

    data class Cosmos(
        @SerializedName("accountNumber")
        val accountNumber: BigInteger,
        @SerializedName("sequence")
        val sequence: BigInteger,
        @SerializedName("gas")
        val gas: BigInteger,
    ) : BlockChainSpecific()

    data class Solana(
        @SerializedName("recentBlockHash")
        val recentBlockHash: String,
        @SerializedName("priorityFee")
        val priorityFee: BigInteger,
        @SerializedName("fromAddressPubKey")
        val fromAddressPubKey: String? = null,
        @SerializedName("toAddressPubKey")
        val toAddressPubKey: String? = null,
    ) : BlockChainSpecific()

    data class Sui(
        @SerializedName("referenceGasPrice")
        val referenceGasPrice: BigInteger,
        @SerializedName("coins")
        val coins: List<Map<String, String>>
    ) : BlockChainSpecific()

    data class Polkadot(
        @SerializedName("recentBlockHash")
        val recentBlockHash: String,
        @SerializedName("nonce")
        val nonce: BigInteger,
        @SerializedName("currentBlockNumber")
        val currentBlockNumber: BigInteger,
        @SerializedName("specVersion")
        val specVersion: UInt,
        @SerializedName("transactionVersion")
        val transactionVersion: UInt,
        @SerializedName("genesisHash")
        val genesisHash: String,
    ) : BlockChainSpecific()
}