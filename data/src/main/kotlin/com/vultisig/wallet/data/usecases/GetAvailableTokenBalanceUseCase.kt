package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import javax.inject.Inject

interface GetAvailableTokenBalanceUseCase : suspend (Account, BigInteger) -> TokenValue?

internal class GetAvailableTokenBalanceUseCaseImpl @Inject constructor(
    private val solanaApi: SolanaApi,
) : GetAvailableTokenBalanceUseCase {

    override suspend fun invoke(
        account: Account,
        gasCost: BigInteger,
    ): TokenValue? {
        val token = account.token
        val tokenValue = account.tokenValue
        val getModifiedToken = when (token.chain) {
            Chain.Solana -> SolanaBalanceModifier(solanaApi)
            else -> DefaultBalanceModifier()
        }
        return if (token.isNativeToken) {
            tokenValue?.copy(
                value = getModifiedToken(tokenValue).value.minus(gasCost)
                    .coerceAtLeast(BigInteger.ZERO),
            )
        } else {
            tokenValue
        }
    }

}

private interface TokenBalanceModifier : suspend (TokenValue) -> TokenValue

private class DefaultBalanceModifier : TokenBalanceModifier {
    override suspend fun invoke(tokenValue: TokenValue) = tokenValue
}

private class SolanaBalanceModifier(
    private val solanaApi: SolanaApi,
) : TokenBalanceModifier {

    override suspend fun invoke(tokenValue: TokenValue): TokenValue {
        val availableTokenValue = calculateAvailableValue(tokenValue)
        return tokenValue.copy(value = availableTokenValue)
    }

    private suspend fun calculateAvailableValue(tokenValue: TokenValue): BigInteger {
        val rentExemption = solanaApi.getMinimumBalanceForRentExemption()
        return (tokenValue.value - rentExemption).coerceAtLeast(BigInteger.ZERO)
    }
}