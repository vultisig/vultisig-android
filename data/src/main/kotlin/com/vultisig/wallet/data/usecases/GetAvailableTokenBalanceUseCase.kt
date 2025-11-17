package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import javax.inject.Inject

interface GetAvailableTokenBalanceUseCase : suspend (Account, BigInteger) -> TokenValue?

internal class GetAvailableTokenBalanceUseCaseImpl @Inject constructor() :
    GetAvailableTokenBalanceUseCase {

    override suspend fun invoke(
        account: Account,
        gasCost: BigInteger,
    ): TokenValue? {
        val token = account.token
        val tokenValue = account.tokenValue
        return if (token.isNativeToken) {
            tokenValue?.copy(
                value = tokenValue.value.minus(gasCost)
                    .coerceAtLeast(BigInteger.ZERO),
            )
        } else {
            tokenValue
        }
    }
}