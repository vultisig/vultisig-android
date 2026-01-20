package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.proto.v1.SignDirectProto
import vultisig.keysign.v1.SignAmino
import vultisig.keysign.v1.SignSolana
import vultisig.keysign.v1.TronTransferAssetContractPayload
import vultisig.keysign.v1.TronTransferContractPayload
import vultisig.keysign.v1.TronTriggerSmartContractPayload
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
    val signAmino: SignAmino? = null,
    val signDirect: SignDirectProto? = null,
    val tronTransferContractPayload: TronTransferContractPayload? = null,
    val tronTriggerSmartContractPayload: TronTriggerSmartContractPayload? = null,
    val tronTransferAssetContractPayload: TronTransferAssetContractPayload? = null,
    val signSolana: SignSolana? = null,
    val skipBroadcast: Boolean = false,
    val defiAction: DeFiAction = DeFiAction.NONE,
)

enum class DeFiAction { NONE, CIRCLE_USDC_WITHDRAW }