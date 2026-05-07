package com.vultisig.wallet.ui.models.send.submit

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import java.math.BigInteger

internal suspend fun GasFeeToEstimatedFeeUseCase.fiatFeesFor(
    gasFee: TokenValue,
    selectedToken: Coin,
): EstimatedGasFee =
    invoke(GasFeeParams(BigInteger.valueOf(1), gasFee = gasFee, selectedToken = selectedToken))
