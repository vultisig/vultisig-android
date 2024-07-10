package com.vultisig.wallet.data.models

import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.presenter.keysign.BlockChainSpecific

internal data class DepositTransaction(
    val id: TransactionId,
    val vaultId: String,

    val srcToken: Coin,
    val srcAddress: String,
    val srcTokenValue: TokenValue,
    val memo: String,
    val dstAddress: String,
    val estimatedFees: TokenValue,
    val blockChainSpecific: BlockChainSpecific,
)
