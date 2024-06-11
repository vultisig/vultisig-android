package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.models.Coin
import kotlin.time.Duration

internal data class SwapTransaction(
    val id: TransactionId,
    val vaultId: String,
    val srcToken: Coin,
    val srcTokenValue: TokenValue,
    val srcAddress: String,
    val dstToken: Coin,
    val dstAddress: String,
    val expectedDstTokenValue: TokenValue,
    val blockChainSpecific: BlockChainSpecificAndUtxo,
    val vaultAddress: String,
    val routerAddress: String?,
    val estimatedFees: TokenValue,
    val estimatedTime: Duration?,
)