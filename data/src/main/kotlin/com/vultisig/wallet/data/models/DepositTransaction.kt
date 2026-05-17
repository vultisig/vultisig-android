package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
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
    val operation: String = "",
    val thorAddress: String = "",
    val nodeAddress: String = "",
    val pairedAddress: String = "",
    val pool: String = "",
    val utxos: List<UtxoInfo> = emptyList(),
    /**
     * Carries THORChain router/vault/expiration for ERC-20 LP add via `depositWithExpiry`. Threaded
     * into `KeysignPayload.swapPayload` so the signing path encodes the router call (and the wire
     * format matches iOS for QR parity). Null for native L1 deposits.
     */
    val swapPayload: SwapPayload? = null,
)

const val OPERATION_MINT = "Mint"
const val OPERATION_WITHDRAW = "Withdraw"
const val OPERATION_BOND = "Bond"
const val OPERATION_UNBOND = "Unbond"
const val OPERATION_LEAVE = "Leave"
const val OPERATION_LOAN_OPEN = "Loan Open"
const val OPERATION_LOAN_CLOSE = "Loan Close"
const val OPERATION_CIRCLE_WITHDRAW = "DepositUSDCCircle"
