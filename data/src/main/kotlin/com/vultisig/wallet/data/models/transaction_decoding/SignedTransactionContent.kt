package com.vultisig.wallet.data.models.transaction_decoding

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigInteger
import vultisig.keysign.v1.TransactionType
import vultisig.keysign.v1.WasmExecuteContractPayload

/** The quantity a transaction moves, or the fact that it does not carry one. */
sealed class SignedAmount {
    /** Base units carried by the transaction. */
    data class Committed(val value: BigInteger) : SignedAmount()

    /**
     * A max-send amount derived from balance and fee during signing. No preview value is exposed to
     * decoders.
     */
    data object ComputedAtSigning : SignedAmount()

    /** Checks equality based on amount type and values. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        return when {
            this is Committed && other is Committed -> value == other.value
            this is ComputedAtSigning && other is ComputedAtSigning -> true
            else -> false
        }
    }

    /** Computes hashCode based on amount type and values. */
    override fun hashCode(): Int {
        return when (this) {
            is Committed -> value.hashCode()
            is ComputedAtSigning -> ComputedAtSigning::class.hashCode()
        }
    }
}

/** How a chain's grammar relates to the routes that are signed before its own helper runs. */
enum class MemoPrecedence {
    /** An earlier approve or swap route makes the sidecar memo inert. */
    MemoIsInertWhenRoutedEarlier,

    /** The earlier route carries the memo into the signed transaction. */
    MemoTravelsWithTheEarlierRoute,
}

/** Coherent set of sidecars withheld together when opaque content wins. */
data class CorroboratedContent(
    val toAddress: String,
    val amount: SignedAmount,
    private val rawMemo: String?,
    val transactionType: TransactionType,
    val wasmPayload: WasmExecuteContractPayload?,
    val swap: SwapPayload?,
    val approve: ERC20ApprovePayload?,
) {
    /** Returns the memo only when the reader's precedence permits it. */
    fun memo(precedence: MemoPrecedence): String? {
        return when (precedence) {
            MemoPrecedence.MemoIsInertWhenRoutedEarlier ->
                if (routedBeforeTheChainHelper) null else rawMemo
            MemoPrecedence.MemoTravelsWithTheEarlierRoute -> rawMemo
        }
    }

    /**
     * Approve and swap routes run before chain helpers. Each memo reader decides whether that makes
     * its memo inert; THORChain swaps carry theirs forward.
     */
    private val routedBeforeTheChainHelper: Boolean
        get() = swap != null || approve != null
}

/**
 * A shared, deliberately narrow view of what initiators and co-signers sign. It excludes display
 * metadata, hides sidecars when opaque content is active, and mirrors the signer's routing
 * precedence.
 */
interface SignedTransactionContent {
    /** The signed chain scope. Display-only `Coin` metadata stays out of reach. */
    val chain: Chain

    /** Whether the chain settles the moved asset as its native coin. */
    val isNativeCoin: Boolean

    // MARK: Ungated fields
    // Decoders should prefer `corroborated`; `raw` access requires explicit proof.

    val rawToAddress: String

    /** The quantity, or the fact that the signer computes it. */
    val rawAmount: SignedAmount

    val signedData: ByteArray?

    /**
     * Whether `signDirect.bodyBytes` is the active body consumed by the signer. Approve, swap, and
     * rebuilt Cosmos routes can make it inactive.
     */
    val signedDataBodyIsActive: Boolean
        get() = false

    /**
     * Whether opaque signed content supersedes all flat sidecar fields. Both devices report this
     * from the representation they hold.
     */
    val hasOpaqueSignedContent: Boolean

    val rawMemo: String?
    val rawTransactionType: TransactionType
    val rawWasmPayload: WasmExecuteContractPayload?
    val rawSwap: SwapPayload?
    val rawApprove: ERC20ApprovePayload?

    /**
     * A Solana pre-image the initiator's signer will encode. Co-signers have signed bytes instead
     * and return `null`.
     */
    val stakingIntent: SolanaStakingPayload?
        get() = null

    /**
     * The Cosmos staking structure that the initiator's signing path will turn into a SignDoc. A
     * co-signer answers `null` and reads that SignDoc instead.
     */
    val cosmosStakingIntent: CosmosStakingPayload?
        get() = null

    /** Whether an earlier signing route makes the memo inert. */
    val memoIsOutranked: Boolean
        get() = rawWasmPayload != null

    /** A coherent set of sidecars, withheld together when opaque content wins. */
    val corroborated: CorroboratedContent?
        get() {
            if (hasOpaqueSignedContent) return null

            return CorroboratedContent(
                toAddress = rawToAddress,
                amount = rawAmount,
                // Wasm and chain-specific higher-priority routes make sidecar memos inert.
                rawMemo = if (memoIsOutranked) null else rawMemo,
                transactionType = rawTransactionType,
                wasmPayload = rawWasmPayload,
                swap = rawSwap,
                approve = rawApprove,
            )
        }
}

// MARK: - A co-signer's view

/** Extension making KeysignPayload implement SignedTransactionContent for co-signer flow. */
fun KeysignPayload.asSignedTransactionContent(): SignedTransactionContent =
    KeysignPayloadContent(this)

private data class KeysignPayloadContent(val payload: KeysignPayload) : SignedTransactionContent {

    override val chain: Chain
        get() = payload.coin.chain

    override val isNativeCoin: Boolean
        get() = payload.coin.isNativeToken

    override val rawToAddress: String
        get() = payload.toAddress

    override val rawAmount: SignedAmount
        get() {
            val sendMaxAmount =
                (payload.blockChainSpecific as? BlockChainSpecific.UTXO)?.sendMaxAmount ?: false
            return if (sendMaxAmount) SignedAmount.ComputedAtSigning
            else SignedAmount.Committed(payload.toAmount)
        }

    override val rawMemo: String?
        get() = payload.memo?.takeIf { it.isNotEmpty() }

    override val rawTransactionType: TransactionType
        get() =
            (payload.blockChainSpecific as? BlockChainSpecific.Cosmos)?.transactionType
                ?: (payload.blockChainSpecific as? BlockChainSpecific.THORChain)?.transactionType
                ?: TransactionType.TRANSACTION_TYPE_UNSPECIFIED

    override val rawWasmPayload: WasmExecuteContractPayload?
        get() = payload.wasmExecuteContractPayload

    override val rawSwap: SwapPayload?
        get() = payload.swapPayload

    override val rawApprove: ERC20ApprovePayload?
        get() = payload.approvePayload

    override val signedData: ByteArray?
        get() = payload.signDirect?.bodyBytes?.toByteArray()

    override val signedDataBodyIsActive: Boolean
        get() {
            val signDirect = payload.signDirect ?: return false
            if (payload.approvePayload != null || payload.swapPayload != null) return false

            return when (payload.coin.chain) {
                Chain.GaiaChain,
                Chain.Kujira,
                Chain.Osmosis,
                Chain.Noble,
                Chain.Akash -> {
                    val txType = rawTransactionType
                    txType == TransactionType.TRANSACTION_TYPE_UNSPECIFIED ||
                        txType == TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT
                }
                Chain.Terra,
                Chain.TerraClassic -> true
                Chain.Dydx -> rawTransactionType != TransactionType.TRANSACTION_TYPE_VOTE
                Chain.Qbtc -> true
                else -> false
            }
        }

    override val hasOpaqueSignedContent: Boolean
        get() = signedDataBodyIsActive

    override val memoIsOutranked: Boolean
        get() {
            return payload.wasmExecuteContractPayload != null ||
                payload.tronTransferContractPayload != null ||
                payload.tronTriggerSmartContractPayload != null ||
                payload.tronTransferAssetContractPayload != null
        }
}

// MARK: - An initiator's view

/**
 * The initiator's pre-payload transaction viewed through the same decoder API. Structured staking
 * intents are exposed because the signer builds from them.
 *
 * Note: Android doesn't have a SendTransaction model like iOS does, so this is a stub
 * implementation. When initiator view is needed, a similar model should be created or this
 * interface should be extended.
 */
data class InitiatingTransactionContent(
    override val chain: Chain,
    override val isNativeCoin: Boolean,
    override val rawToAddress: String,
    override val rawAmount: SignedAmount,
    override val rawMemo: String?,
    override val rawTransactionType: TransactionType,
    override val rawWasmPayload: WasmExecuteContractPayload?,
    override val stakingIntent: SolanaStakingPayload?,
    override val cosmosStakingIntent: CosmosStakingPayload?,
) : SignedTransactionContent {

    override val rawSwap: SwapPayload? = null

    override val rawApprove: ERC20ApprovePayload? = null

    override val signedData: ByteArray? = null

    /** Staking intents become opaque signed content when the payload is built. */
    override val hasOpaqueSignedContent: Boolean
        get() = cosmosStakingIntent != null || stakingIntent != null
}
