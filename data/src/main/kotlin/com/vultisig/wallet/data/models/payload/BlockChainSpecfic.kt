package com.vultisig.wallet.data.models.payload

import vultisig.keysign.v1.CosmosIbcDenomTrace
import vultisig.keysign.v1.SuiCoin
import vultisig.keysign.v1.TransactionType
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
        val transactionType: TransactionType,
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
        val ibcDenomTraces: CosmosIbcDenomTrace?,
        val transactionType: TransactionType,
    ) : BlockChainSpecific()

    data class Solana(
        val recentBlockHash: String,
        val priorityFee: BigInteger,
        val computeLimit: BigInteger,
        val fromAddressPubKey: String? = null,
        val toAddressPubKey: String? = null,
        val programId: Boolean = false,
    ) : BlockChainSpecific()

    data class Sui(
        val referenceGasPrice: BigInteger,
        val gasBudget: BigInteger,
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
        val isDeposit: Boolean = false,
        val sendMaxAmount: Boolean = false,
        val jettonAddress: String = "",
        val isActiveDestination: Boolean = false,
    ) : BlockChainSpecific()

    data class Ripple(
        val sequence: ULong,
        val gas: ULong,
        val lastLedgerSequence: ULong,
    ) : BlockChainSpecific()

    data class Tron(
        val timestamp: ULong,
        val expiration: ULong,
        val blockHeaderTimestamp: ULong,
        val blockHeaderNumber: ULong,
        val blockHeaderVersion: ULong,
        val blockHeaderTxTrieRoot: String,
        val blockHeaderParentHash: String,
        val blockHeaderWitnessAddress: String,
        val gasFeeEstimation : ULong,
    ) : BlockChainSpecific()

    data class Cardano(
        val byteFee: Long,
        val sendMaxAmount: Boolean,
        val ttl: ULong
    ) : BlockChainSpecific()
}