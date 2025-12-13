package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import vultisig.keysign.v1.WasmExecuteContractPayload

data class DepositTransaction(
    val id: TransactionId,
    val vaultId: String,
    val srcToken: Coin,
    val srcAddress: String,
    val srcTokenValue: TokenValue,
    val memo: String,
    val dstAddress: String,
    val estimatedFees: TokenValue,
    val estimateFeesFiat: String,
    val blockChainSpecific: BlockChainSpecific,
    val wasmExecuteContractPayload: WasmExecuteContractPayload? = null,
    val operation: String  = "",
    val thorAddress : String  = "",
)
