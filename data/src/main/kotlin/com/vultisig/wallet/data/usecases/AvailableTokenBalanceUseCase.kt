package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import javax.inject.Inject

interface AvailableTokenBalanceUseCase : suspend (Account, BigInteger) -> TokenValue?

class AvailableTokenBalanceUseCaseImpl @Inject constructor(
    private val solanaApi: SolanaApi,
) : AvailableTokenBalanceUseCase {

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
                value = maxOf(
                    BigInteger.ZERO,
                    getModifiedToken(account)?.value?.minus(gasCost)
                        ?: BigInteger.ZERO
                ),
            )
        } else {
            tokenValue
        }
    }

}

private interface TokenBalanceModifier : suspend (Account) -> TokenValue?

private class DefaultBalanceModifier : TokenBalanceModifier {
    override suspend fun invoke(account: Account): TokenValue? {
        return account.tokenValue
    }
}

private class SolanaBalanceModifier(
    private val solanaApi: SolanaApi,
) : TokenBalanceModifier {

    override suspend fun invoke(account: Account): TokenValue? {
        val tokenValue = account.tokenValue
        val adjustedValue = tokenValue?.value.takeIf { !account.token.isNativeToken }
            ?: calculateAvailableValue(tokenValue)
        return tokenValue?.copy(value = adjustedValue)
    }

    private suspend fun calculateAvailableValue(tokenValue: TokenValue?): BigInteger {
        val rentExemption = solanaApi.getMinimumBalanceForRentExemption()
        return if (tokenValue == null) BigInteger.ZERO
        else maxOf(BigInteger.ZERO, tokenValue.value - rentExemption)
    }
}