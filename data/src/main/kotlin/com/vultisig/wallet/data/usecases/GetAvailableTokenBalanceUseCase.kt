package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.blockchain.cosmos.TerraClassicTax
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import javax.inject.Inject

interface GetAvailableTokenBalanceUseCase : suspend (Account, BigInteger) -> TokenValue?

internal class GetAvailableTokenBalanceUseCaseImpl @Inject constructor() :
    GetAvailableTokenBalanceUseCase {

    override suspend fun invoke(account: Account, gasCost: BigInteger): TokenValue? {
        val token = account.token
        val tokenValue = account.tokenValue
        // Terra Classic bank denoms (e.g. USTC/uusd) pay gas + burn tax in their OWN denom, not in
        // native LUNC — so, like native tokens, the fee must be reserved out of the token's own
        // balance. Without this a MAX send signs amount+fee > balance and the chain rejects it.
        // Mirrors iOS terraClassicMaxValue. CW20/IBC still pay in native LUNC and reserve nothing.
        val feePaidInThisToken =
            token.isNativeToken ||
                (token.chain == Chain.TerraClassic &&
                    TerraClassicTax.isBankDenom(token.contractAddress, token.isNativeToken))
        return if (feePaidInThisToken) {
            tokenValue?.copy(value = tokenValue.value.minus(gasCost).coerceAtLeast(BigInteger.ZERO))
        } else {
            tokenValue
        }
    }
}
