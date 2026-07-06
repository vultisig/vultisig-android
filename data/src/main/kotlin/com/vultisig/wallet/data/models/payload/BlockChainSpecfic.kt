package com.vultisig.wallet.data.models.payload

import java.math.BigInteger
import vultisig.keysign.v1.CosmosIbcDenomTrace
import vultisig.keysign.v1.SuiCoin
import vultisig.keysign.v1.TransactionType

sealed class BlockChainSpecific {
    /**
     * @param zcashBranchId live ZIP-243 consensus branch id (little-endian hex, e.g. `30f33754`)
     *   fetched at send time for Zcash; null for every other UTXO chain and when the RPC was
     *   unreachable, in which case signing refuses (there is no compiled-in fallback). Transient:
     *   it is NOT carried by the `UTXOSpecific` proto, so a co-signing device that rebuilds the
     *   payload from proto must repopulate it (see JoinKeysignViewModel) before signing.
     */
    data class UTXO(
        val byteFee: BigInteger,
        val sendMaxAmount: Boolean,
        val zcashBranchId: String? = null,
    ) : BlockChainSpecific()

    /**
     * Marker variant for Bitcoin PSBT co-signing initiated by external dApps. The structured
     * inputs/outputs live on `KeysignPayload.signBitcoin`; this entry exists so the
     * `blockChainSpecific` slot stays non-null when the proto omits a `utxo_specific` block.
     */
    data object BitcoinPSBT : BlockChainSpecific()

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
        // Relayed per-tx gas limit (proto `CosmosSpecific.gas_limit`) from a
        // `/cosmos/tx/v1beta1/simulate` estimate. null means "use the static per-chain gas
        // limit". It is part of the SignDoc, so every co-signing device must apply it
        // identically or the MPC signature fails.
        val gasLimit: BigInteger? = null,
    ) : BlockChainSpecific()

    data class Solana(
        val recentBlockHash: String,
        val priorityFee: BigInteger,
        val priorityLimit: BigInteger,
        val fromAddressPubKey: String? = null,
        val toAddressPubKey: String? = null,
        val programId: Boolean? = false,
    ) : BlockChainSpecific()

    data class Sui(
        val referenceGasPrice: BigInteger,
        val gasBudget: BigInteger,
        val coins: List<SuiCoin>,
    ) : BlockChainSpecific()

    data class Polkadot(
        val recentBlockHash: String,
        val nonce: BigInteger,
        val currentBlockNumber: BigInteger,
        val specVersion: UInt,
        val transactionVersion: UInt,
        val genesisHash: String,
        val gas: ULong,
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

    data class Ripple(val sequence: ULong, val gas: ULong, val lastLedgerSequence: ULong) :
        BlockChainSpecific()

    data class Tron(
        val timestamp: ULong,
        val expiration: ULong,
        val blockHeaderTimestamp: ULong,
        val blockHeaderNumber: ULong,
        val blockHeaderVersion: ULong,
        val blockHeaderTxTrieRoot: String,
        val blockHeaderParentHash: String,
        val blockHeaderWitnessAddress: String,
        val gasFeeEstimation: ULong,
    ) : BlockChainSpecific()

    data class Cardano(val byteFee: Long, val sendMaxAmount: Boolean, val ttl: ULong) :
        BlockChainSpecific()
}

/**
 * Live ZIP-243 branch id carried on a Zcash payload's UTXO specific, or null for non-Zcash payloads
 * and when the RPC was unreachable (signing then refuses; there is no compiled-in fallback).
 */
val KeysignPayload.zcashBranchId: String?
    get() = (blockChainSpecific as? BlockChainSpecific.UTXO)?.zcashBranchId
