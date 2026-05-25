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
    val gasFees: TokenValue
    val memo: String?
    val payload: SwapPayload
    val isApprovalRequired: Boolean
    val gasFeeFiatValue: FiatValue

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
        override val gasFees: TokenValue,
        override val memo: String?,
        override val payload: SwapPayload,
        override val isApprovalRequired: Boolean,
        override val gasFeeFiatValue: FiatValue,
    ) : SwapTransaction

    companion object {
        val maxAllowance: BigInteger
            get() = BigInteger("2".repeat(64), 16)
    }
}
