package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.models.Coin
import java.math.BigInteger
import kotlin.time.Duration

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
    val estimatedTime: Duration?,
    val payload: SwapPayload,
    val isApprovalRequired: Boolean,
) {

    companion object {
        val maxAllowance: BigInteger
            get() = BigInteger("2".repeat(64), 16)
    }

}
