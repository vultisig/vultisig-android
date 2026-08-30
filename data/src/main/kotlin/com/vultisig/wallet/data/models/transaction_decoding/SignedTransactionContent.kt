package com.vultisig.wallet.data.models.transaction_decoding

import com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload
import com.vultisig.wallet.data.blockchain.solana.staking.SolanaStakingPayload
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import java.math.BigInteger
import vultisig.keysign.v1.SignAmino
import vultisig.keysign.v1.SignBitcoin
import vultisig.keysign.v1.SignRipple
import vultisig.keysign.v1.SignSui
import vultisig.keysign.v1.SignTon
import vultisig.keysign.v1.TransactionType
import vultisig.keysign.v1.WasmExecuteContractPayload
import wallet.core.jni.Base64

/**
 * Union type representing the different forms of opaque signed content across blockchains. Each
 * variant carries the transaction content the signer works from instead of the flat sidecar fields,
 * but not every variant is byte-final: some are pre-encoded bodies that travel into the signing
 * input untouched, while others are structured content a chain helper re-encodes together with the
 * payload's chain-specific data. Each variant states which of the two it is.
 */
sealed class OpaqueSignedContent {
    /**
     * Legacy Cosmos amino JSON messages. `CosmosHelper` and `ThorChainHelper` copy each message
     * into the signing input as raw JSON without ever reading `toAddress` or `toAmount`, so these
     * messages — not the flat sidecars — are what gets signed.
     */
    data class CosmosSignAmino(val signAmino: SignAmino) : OpaqueSignedContent()

    /**
     * Cosmos SignDoc body bytes (the canonical form for Cosmos chains). The bytes are the result of
     * decoding the base64 SignDirectProto.bodyBytes and travel into the signing input unchanged.
     */
    data class CosmosSignDirect(val bodyBytes: ByteArray) : OpaqueSignedContent() {
        override fun equals(other: Any?) =
            other is CosmosSignDirect && bodyBytes.contentEquals(other.bodyBytes)

        override fun hashCode() = bodyBytes.contentHashCode()
    }

    /** Solana message transactions signed as given (base64-encoded). */
    data class SolanaSignature(val rawTransactions: List<String>) : OpaqueSignedContent()

    /**
     * TON transaction messages. Not byte-final: `TonHelper` re-encodes every message into a
     * `TheOpenNetwork.Transfer` and combines it with the payload's TON chain-specific data
     * (sequence number, expiry, bounceable flag), so these messages describe what is signed without
     * being the signed bytes themselves.
     */
    data class TonTransaction(val signTon: SignTon) : OpaqueSignedContent()

    /** Sui Programmable Transaction Block bytes signed verbatim. */
    data class SuiTransaction(val signSui: SignSui) : OpaqueSignedContent()

    /** XRPL transaction JSON signed verbatim. */
    data class RippleTransaction(val signRipple: SignRipple) : OpaqueSignedContent()

    /** Bitcoin PSBT inputs and outputs signed as given, bypassing transaction planning. */
    data class BitcoinPSBT(val signBitcoin: SignBitcoin) : OpaqueSignedContent()
}

/** The quantity a transaction moves, or the fact that it does not carry one. */
sealed class SignedAmount {
    /** Base units carried by the transaction. */
    data class Committed(val value: BigInteger) : SignedAmount()

    /**
     * A max-send amount derived from balance and fee during signing. No preview value is exposed to
     * decoders.
     */
    data object ComputedAtSigning : SignedAmount()
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

    /**
     * The opaque signed content if present — one of several transaction forms (Cosmos amino
     * messages, Cosmos SignDoc, Solana message, etc.) that the signer works from in place of the
     * flat sidecar fields. See [OpaqueSignedContent] for which forms are byte-final.
     */
    val signedData: OpaqueSignedContent?

    /**
     * Whether the active signed content body is opaque. Approve, swap, and rebuilt Cosmos routes
     * can make it inactive.
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

/**
 * Chains whose signing path is driven by a dApp-supplied Cosmos sign document — amino JSON or a
 * protobuf SignDoc — rather than by a rebuilt bank/CW20 message.
 */
private val COSMOS_SIGN_DOC_CHAINS =
    setOf(
        Chain.GaiaChain,
        Chain.Osmosis,
        Chain.Noble,
        Chain.Akash,
        Chain.Terra,
        Chain.TerraClassic,
        Chain.Dydx,
        Chain.Qbtc,
        Chain.ThorChain,
        Chain.MayaChain,
    )

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
                (payload.blockChainSpecific as? BlockChainSpecific.UTXO)?.sendMaxAmount
                    ?: (payload.blockChainSpecific as? BlockChainSpecific.Cardano)?.sendMaxAmount
                    ?: (payload.blockChainSpecific as? BlockChainSpecific.Ton)?.sendMaxAmount
                    ?: false
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

    override val signedData: OpaqueSignedContent?
        get() {
            // signAmino is read before signDirect in CosmosHelper and ThorChainHelper.
            payload.signAmino?.let { amino ->
                return OpaqueSignedContent.CosmosSignAmino(amino)
            }
            payload.signDirect?.bodyBytes?.let { bodyBytes ->
                return OpaqueSignedContent.CosmosSignDirect(Base64.decode(bodyBytes))
            }
            payload.signSolana?.let { solana ->
                return OpaqueSignedContent.SolanaSignature(solana.rawTransactions)
            }
            payload.signTon?.let { ton ->
                return OpaqueSignedContent.TonTransaction(ton)
            }
            payload.signSui?.let { sui ->
                return OpaqueSignedContent.SuiTransaction(sui)
            }
            payload.signRipple?.let { ripple ->
                return OpaqueSignedContent.RippleTransaction(ripple)
            }
            payload.signBitcoin?.let { bitcoin ->
                return OpaqueSignedContent.BitcoinPSBT(bitcoin)
            }
            return null
        }

    override val signedDataBodyIsActive: Boolean
        get() {
            val chain = payload.coin.chain
            // signAmino is read before the swap route and before signDirect in CosmosHelper and
            // ThorChainHelper — including inside the THORChain swap path — so whenever it is
            // present its messages, not the sidecars, are what gets signed.
            if (payload.signAmino != null) return chain in COSMOS_SIGN_DOC_CHAINS
            // Approve and swap routes make opaque content inactive
            if (payload.approvePayload != null || payload.swapPayload != null) return false

            return when (chain) {
                // Cosmos chains and related protocols use signDirect when present
                in COSMOS_SIGN_DOC_CHAINS -> payload.signDirect != null
                // TON, Sui, Solana, Bitcoin, Ripple use their signed content when present
                Chain.Ton -> payload.signTon != null
                Chain.Sui -> payload.signSui != null
                Chain.Solana -> payload.signSolana != null
                Chain.Bitcoin,
                Chain.BitcoinCash,
                Chain.Dogecoin,
                Chain.Litecoin -> payload.signBitcoin != null
                Chain.Ripple -> payload.signRipple != null
                else -> false
            }
        }

    override val hasOpaqueSignedContent: Boolean
        get() = signedData != null && signedDataBodyIsActive

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

    override val signedData: OpaqueSignedContent? = null

    /** Staking intents become opaque signed content when the payload is built. */
    override val hasOpaqueSignedContent: Boolean
        get() = cosmosStakingIntent != null || stakingIntent != null
}
