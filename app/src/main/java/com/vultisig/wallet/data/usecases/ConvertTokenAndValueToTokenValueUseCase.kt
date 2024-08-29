package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import javax.inject.Inject

internal interface ConvertTokenAndValueToTokenValueUseCase : (Coin, BigInteger) -> TokenValue

internal class ConvertTokenAndValueToTokenValueUseCaseImpl @Inject constructor() :
    ConvertTokenAndValueToTokenValueUseCase {

    override fun invoke(token: Coin, value: BigInteger): TokenValue = TokenValue(
        value = value,
        unit = token.ticker,
        decimals = token.decimal
    )

}