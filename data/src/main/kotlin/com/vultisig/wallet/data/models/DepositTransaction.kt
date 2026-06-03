package com.vultisig.wallet.data.models

import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
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
     * Pre-built `signDirect` artefacts when the deposit carries an opaque, app-built SignDoc (e.g.
     * Cosmos-SDK x/staking + x/distribution msgs for Terra LUNA / LUNC). When set, the keysign
     * payload builder forwards these bytes to `CosmosHelper.buildSignDirectSigningInput` and skips
     * the default Cosmos `MsgSend` path. Null for all other deposit flows.
     */
    val signDirect: SignDirectProto? = null,
    /**
     * Structured staking intent (op type + validator addresses) used purely to render the
     * staking-specific Verify summary — "You're staking / unstaking / moving / claiming". The
     * SignDoc bytes in [signDirect] are the signing contract; this field is display-only and not
     * part of the keysign payload.
     */
    val cosmosStakingPayload:
        com.vultisig.wallet.data.blockchain.cosmos.staking.CosmosStakingPayload? =
        null,
)

const val OPERATION_MINT = "Mint"
const val OPERATION_WITHDRAW = "Withdraw"
const val OPERATION_BOND = "Bond"
const val OPERATION_UNBOND = "Unbond"
const val OPERATION_LEAVE = "Leave"
const val OPERATION_LOAN_OPEN = "Loan Open"
const val OPERATION_LOAN_CLOSE = "Loan Close"
const val OPERATION_CIRCLE_WITHDRAW = "DepositUSDCCircle"
