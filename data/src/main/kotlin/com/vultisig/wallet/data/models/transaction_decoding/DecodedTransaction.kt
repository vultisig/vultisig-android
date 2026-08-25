package com.vultisig.wallet.data.models.transaction_decoding

import java.math.BigInteger

/** What the amount is denominated in. */
sealed class DecodedAsset {
    /** A denomination named by signed content. */
    data class Denom(val value: String) : DecodedAsset()

    /** The transaction coin; its display metadata is resolved separately. */
    data object TransactionCoin : DecodedAsset()

    /** The instruction's fixed native asset, rendered from bundled metadata. */
    data object ChainNative : DecodedAsset()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return when {
            this is Denom && other is Denom -> value == other.value
            this is TransactionCoin && other is TransactionCoin -> true
            this is ChainNative && other is ChainNative -> true
            else -> false
        }
    }

    override fun hashCode(): Int {
        return when (this) {
            is Denom -> value.hashCode()
            is TransactionCoin -> TransactionCoin::class.hashCode()
            is ChainNative -> ChainNative::class.hashCode()
        }
    }
}

/** The quantity, or the share of a position, a transaction moves. */
sealed class DecodedAmount {
    /** Base units exactly as the signed content carries them. */
    data class Units(val value: BigInteger, val asset: DecodedAsset) : DecodedAmount()

    /** A signed share in basis points; resolving the position is enrichment. */
    data class Fraction(val basisPoints: Int, val asset: DecodedAsset) : DecodedAmount()

    /** The signed operation names no quantity. */
    data object Unstated : DecodedAmount()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return when {
            this is Units && other is Units -> value == other.value && asset == other.asset
            this is Fraction && other is Fraction ->
                basisPoints == other.basisPoints && asset == other.asset
            this is Unstated && other is Unstated -> true
            else -> false
        }
    }

    override fun hashCode(): Int {
        return when (this) {
            is Units -> value.hashCode() * 31 + asset.hashCode()
            is Fraction -> basisPoints * 31 + asset.hashCode()
            is Unstated -> Unstated::class.hashCode()
        }
    }
}

/** One asset-independent verb per operation. */
enum class DecodedOperation {
    Transfer,
    Swap,
    Approve,
    Stake,
    Unstake,
    Bond,
    Unbond,
    Rebond,
    Leave,
    Delegate,
    Undelegate,
    Redelegate,
    ClaimRewards,
    Mint,
    Redeem,
    AddLiquidity,
    RemoveLiquidity,
    Merge,
    Unmerge,
    IbcTransfer,
    Vote,
    SecuredAssetDeposit,
    SecuredAssetWithdraw,
    SwitchChain,
    LimitOrderPlacement,
    LimitOrderCancel,
    ContractCall,
    Unknown,
}

/** Who or what the operation is directed at, when the transaction names one. */
sealed class DecodedCounterparty {
    data class Node(val value: String) : DecodedCounterparty()

    data class Validator(val value: String) : DecodedCounterparty()

    data class Pool(val value: String) : DecodedCounterparty()

    data class Contract(val value: String) : DecodedCounterparty()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return when {
            this is Node && other is Node -> value == other.value
            this is Validator && other is Validator -> value == other.value
            this is Pool && other is Pool -> value == other.value
            this is Contract && other is Contract -> value == other.value
            else -> false
        }
    }

    override fun hashCode(): Int {
        return when (this) {
            is Node -> value.hashCode()
            is Validator -> value.hashCode()
            is Pool -> value.hashCode()
            is Contract -> value.hashCode()
        }
    }
}

/** Evidence for the reading, ordered strongest first. */
enum class DecodedEvidence(val strength: Int) {
    /** The literal object being signed — a SignDoc, a raw transaction, a BOC. */
    SignedData(0),

    /** The contract call that gets signed, naming its own action. */
    WasmExecuteMsg(1),

    /** The string the chain itself parses. */
    Memo(2),

    /** Structured intent that builds the signed bytes. */
    StructuredPayload(3),

    /** The wire discriminator that selects the signing shape. */
    WireTransactionType(4),

    /** Nothing was read. Only ever paired with `.unknown`. */
    Unread(5);

    fun isNoWeaker(than: DecodedEvidence): Boolean = strength <= than.strength
}

/**
 * A provenance-aware reading of signed transaction content. Amounts remain in raw base units;
 * scaling with unsigned display metadata happens later.
 */
data class DecodedTransaction(
    val operation: DecodedOperation,
    val amount: DecodedAmount,
    val counterparty: DecodedCounterparty? = null,
    val evidence: DecodedEvidence,
) {
    companion object {
        /** Nothing readable identified this transaction. */
        val unreadable =
            DecodedTransaction(
                operation = DecodedOperation.Unknown,
                amount = DecodedAmount.Unstated,
                evidence = DecodedEvidence.Unread,
            )
    }
}
