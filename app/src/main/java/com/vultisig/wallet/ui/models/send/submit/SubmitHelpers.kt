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
 * What this payload will actually cost the balance on EVM: op-geth reserves `gasLimit *
 * maxFeePerGas` before executing, plus the OP-stack L1 data fee it bills alongside but that no
 * transaction field carries. Every other chain returns [gasFee] unchanged.
 *
 * Read it only once any Advanced Gas Settings override has been applied, so it reflects the numbers
 * that are signed rather than the ones the fee service proposed.
 */
internal fun BlockChainSpecificAndUtxo.signedGasFee(gasFee: TokenValue): TokenValue {
    val eth = blockChainSpecific as? BlockChainSpecific.Ethereum ?: return gasFee
    return gasFee.copy(value = eth.gasLimit * eth.maxFeePerGasWei + extraFeeReserve)
}
