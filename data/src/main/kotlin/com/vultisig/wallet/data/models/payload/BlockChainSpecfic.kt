package com.vultisig.wallet.data.models.payload

import vultisig.keysign.v1.SuiCoin
import java.math.BigInteger

sealed class BlockChainSpecific {
    data class UTXO(
        val byteFee: BigInteger,
        val sendMaxAmount: Boolean
    ) : BlockChainSpecific()

    data class Ethereum(
        val maxFeePerGasWei: BigInteger,
        val priorityFeeWei: BigInteger,
        val nonce: BigInteger,
        val gasLimit: BigInteger,
    ) : BlockChainSpecific()

    data class THORChain(
        val accountNumber: BigInteger,
        val sequence: BigInteger,
        val fee: BigInteger,
        val isDeposit: Boolean,
    ) : BlockChainSpecific()

    data class MayaChain(
        val accountNumber: BigInteger,
        val sequence: BigInteger,
        val isDeposit: Boolean,
    ) : BlockChainSpecific()

    data class Cosmos(
        val accountNumber: BigInteger,
        val sequence: BigInteger,
        val gas: BigInteger,
    ) : BlockChainSpecific()

    data class Solana(
        val recentBlockHash: String,
        val priorityFee: BigInteger,
        val fromAddressPubKey: String? = null,
        val toAddressPubKey: String? = null,
    ) : BlockChainSpecific()

    data class Sui(
        val referenceGasPrice: BigInteger,
        val coins: List<SuiCoin>
    ) : BlockChainSpecific()

    data class Polkadot(
        val recentBlockHash: String,
        val nonce: BigInteger,
        val currentBlockNumber: BigInteger,
        val specVersion: UInt,
        val transactionVersion: UInt,
        val genesisHash: String,
    ) : BlockChainSpecific()

    data class Ton(
        val sequenceNumber: ULong,
        val expireAt: ULong,
        val bounceable: Boolean,
    ) : BlockChainSpecific()

}