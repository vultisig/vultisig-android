package com.vultisig.wallet.ui.models.send.submit

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import java.math.BigInteger

internal suspend fun GasFeeToEstimatedFeeUseCase.fiatFeesFor(
    gasFee: TokenValue,
    selectedToken: Coin,
): EstimatedGasFee =
    invoke(GasFeeParams(BigInteger.valueOf(1), gasFee = gasFee, selectedToken = selectedToken))

/**
 * What an EVM transaction reserves from the balance, denominated like [gasFee]: op-geth checks
 * `value + gasLimit * maxFeePerGas + l1Cost` before executing, and only the gas product is carried
 * by a signed field. Null off EVM. Valid once Advanced Gas Settings have been applied.
 */
internal fun BlockChainSpecificAndUtxo.evmSignedFee(gasFee: TokenValue): TokenValue? {
    val eth = blockChainSpecific as? BlockChainSpecific.Ethereum ?: return null
    return gasFee.copy(value = eth.gasLimit * eth.maxFeePerGasWei + extraFeeReserve)
}
