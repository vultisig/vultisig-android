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
}

/** The quantity, or the share of a position, a transaction moves. */
sealed class DecodedAmount {
    /** Base units exactly as the signed content carries them. */
    data class Units(val value: BigInteger, val asset: DecodedAsset) : DecodedAmount()

    /** A signed share in basis points; resolving the position is enrichment. */
    data class Fraction(val basisPoints: Int, val asset: DecodedAsset) : DecodedAmount()

    /** The signed operation names no quantity. */
    data object Unstated : DecodedAmount()
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
    /** A network node. */
    data class Node(val value: String) : DecodedCounterparty()

    /** A validator. */
    data class Validator(val value: String) : DecodedCounterparty()

    /** A liquidity pool or similar aggregated asset. */
    data class Pool(val value: String) : DecodedCounterparty()

    /** A smart contract. */
    data class Contract(val value: String) : DecodedCounterparty()
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

    /**
     * Checks whether this evidence is at least as strong as [than]. Lower strength values are
     * stronger, so this is true when strength <= than.strength.
     */
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
