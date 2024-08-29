package com.vultisig.wallet.data.models.payload

import com.vultisig.wallet.data.models.Coin
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
)