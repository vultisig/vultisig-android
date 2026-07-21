package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import java.math.BigInteger

sealed interface SwapTransaction {
    val id: TransactionId
    val vaultId: String
    val srcToken: Coin
    val srcTokenValue: TokenValue
    val dstToken: Coin
    val dstAddress: String
    /**
     * ERC20 `approve` spender used when [isApprovalRequired]. Usually equals [dstAddress] — the
     * router we send the swap to is also the allowance target — but providers that route through a
     * separate token-transfer proxy (e.g. SwapKit) must approve that proxy instead. Approving the
     * wrong contract makes the swap revert with ERC20InsufficientAllowance.
     */
    val approveSpender: String
    val expectedDstTokenValue: TokenValue
    val blockChainSpecific: BlockChainSpecificAndUtxo
    val estimatedFees: TokenValue
    /**
     * Affiliate (swap) fee portion of [estimatedFees], denominated in [dstToken]. Non-null only for
     * providers that report a fee breakdown (THORChain / MayaChain); null for aggregators whose
     * [estimatedFees] is a single opaque total. Lets the verify/overview screen show the same
     * affiliate-only "Swap Fee" the swap form shows instead of the full total (#5061).
     */
    val swapFee: TokenValue?
    /**
     * Outbound fee portion of [estimatedFees], denominated in [dstToken]. Non-null alongside
     * [swapFee] for THORChain / MayaChain; rendered as its own "Outbound Fee" row so the verify
     * screen reconciles to the same breakdown the swap form shows (#5061).
     */
    val outboundFee: TokenValue?
    val gasFees: TokenValue
    val memo: String?
    val payload: SwapPayload
    val isApprovalRequired: Boolean
    val gasFeeFiatValue: FiatValue

    /**
     * Display-only fee/discount context carried from the swap form so the verify screen renders the
     * same Swap Fee percentage and VULT-tier / referral discount rows the form shows, instead of a
     * bare total (#5358). All fields are in-memory only (this model is never serialized) and
     * default to "unavailable", so a co-signer rebuilding the tx from the signed payload — which
     * cannot know the initiator's tier — degrades gracefully to no discount rows.
     */
    val swapFeePercent: String?
    /** True when the affiliate fee is baked into the quoted rate (1inch) — see [swapFeePercent]. */
    val swapFeeIncludedInRate: Boolean
    val vultBpsDiscount: Int?
    val vultBpsDiscountFiatValue: String?
    val referralBpsDiscount: Int?
    val referralBpsDiscountFiatValue: String?

    data class RegularSwapTransaction(
        override val id: TransactionId,
        override val vaultId: String,
        override val srcToken: Coin,
        override val srcTokenValue: TokenValue,
        override val dstToken: Coin,
        override val dstAddress: String,
        override val approveSpender: String = dstAddress,
        override val expectedDstTokenValue: TokenValue,
        override val blockChainSpecific: BlockChainSpecificAndUtxo,
        override val estimatedFees: TokenValue,
        override val swapFee: TokenValue? = null,
        override val outboundFee: TokenValue? = null,
        override val gasFees: TokenValue,
        override val memo: String?,
        override val payload: SwapPayload,
        override val isApprovalRequired: Boolean,
        override val gasFeeFiatValue: FiatValue,
        override val swapFeePercent: String? = null,
        override val swapFeeIncludedInRate: Boolean = false,
        override val vultBpsDiscount: Int? = null,
        override val vultBpsDiscountFiatValue: String? = null,
        override val referralBpsDiscount: Int? = null,
        override val referralBpsDiscountFiatValue: String? = null,
        // User-chosen external recipient the output was routed to, or null when it goes to the
        // vault's own address. Surfaced on the verify screen so the destination is never a silent
        // default (#4858).
        val externalRecipient: String? = null,
    ) : SwapTransaction

    companion object {
        val maxAllowance: BigInteger
            get() = BigInteger("2".repeat(64), 16)
    }
}
