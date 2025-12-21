package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import vultisig.keysign.v1.WasmExecuteContractPayload
import java.math.BigInteger

data class KeysignPayload(
    val coin: Coin,
    val toAddress: String,
    val toAmount: BigInteger,
    val blockChainSpecific: BlockChainSpecific,
    val utxos: List<UtxoInfo> = emptyList(),
    val memo: String? = null,
    val swapPayload: SwapPayload? = null,
    val approvePayload: ERC20ApprovePayload? = null,
    val vaultPublicKeyECDSA: String,
    val vaultLocalPartyID: String,
    val libType: SigningLibType?,
    val wasmExecuteContractPayload: WasmExecuteContractPayload?,
    val skipBroadcast: Boolean = false,
    val defiAction: DeFiAction = DeFiAction.NONE,
)

enum class DeFiAction { NONE, CIRCLE_USDC_WITHDRAW }