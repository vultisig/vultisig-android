package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.NearApi
import com.vultisig.wallet.data.api.storageReserve
import com.vultisig.wallet.data.blockchain.cosmos.TerraClassicTax
import com.vultisig.wallet.data.chains.helpers.BittensorHelper
import com.vultisig.wallet.data.chains.helpers.PolkadotHelper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TokenValue
import java.math.BigInteger
import javax.inject.Inject

interface GetAvailableTokenBalanceUseCase : suspend (Account, BigInteger) -> TokenValue?

internal class GetAvailableTokenBalanceUseCaseImpl
@Inject
constructor(private val nearApi: NearApi) : GetAvailableTokenBalanceUseCase {

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
        if (!feePaidInThisToken) return tokenValue
        val balance = tokenValue ?: return null

        // Polkadot and Bittensor reap (deactivate) an account whose free balance drops below the
        // existential deposit, so that reserve must be excluded from the selectable balance the
        // same way gas is. Both chains are signed as `transfer_keep_alive`, which the runtime
        // rejects outright when the send would cross the deposit, so without this term a MAX send
        // fails on-chain with the fee already burned. NEAR shows the raw balance, so its storage
        // reserve is read live and a failed read throws. Ripple needs no equivalent term here:
        // RippleApi.getBalance() already nets the live account reserve out of tokenValue before it
        // reaches this use case, so subtracting it again would double-reserve and under-fill
        // MAX/percentage sends.
        val reserve =
            when {
                token.chain == Chain.Polkadot && token.isNativeToken ->
                    PolkadotHelper.DEFAULT_EXISTENTIAL_DEPOSIT.toBigInteger()

                token.chain == Chain.Bittensor && token.isNativeToken ->
                    BittensorHelper.DEFAULT_EXISTENTIAL_DEPOSIT.toBigInteger()

                token.chain == Chain.Near && token.isNativeToken ->
                    nearApi.storageReserve(token.address)

                else -> BigInteger.ZERO
            }

        return balance.copy(
            value = balance.value.minus(gasCost).minus(reserve).coerceAtLeast(BigInteger.ZERO)
        )
    }
}
