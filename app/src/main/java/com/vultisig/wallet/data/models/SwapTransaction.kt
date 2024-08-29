package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import java.math.BigInteger

internal data class SwapTransaction(
    val id: TransactionId,
    val vaultId: String,
    val srcToken: Coin,
    val srcTokenValue: TokenValue,
    val dstToken: Coin,
    val dstAddress: String,
    val expectedDstTokenValue: TokenValue,
    val blockChainSpecific: BlockChainSpecificAndUtxo,
    val estimatedFees: TokenValue,
    val memo: String?,
    val payload: SwapPayload,
    val isApprovalRequired: Boolean,
) {

    companion object {
        val maxAllowance: BigInteger
            get() = BigInteger("2".repeat(64), 16)
    }

}
