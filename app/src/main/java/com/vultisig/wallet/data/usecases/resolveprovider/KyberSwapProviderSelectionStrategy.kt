package com.vultisig.wallet.data.usecases.resolveprovider

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import java.math.BigDecimal
import javax.inject.Inject

internal interface KyberSwapProviderSelectionStrategy : SwapProviderSelectionStrategy

internal class KyberSwapProviderSelectionStrategyImpl @Inject constructor(
    private val swapQuoteRepository: SwapQuoteRepository,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
) : KyberSwapProviderSelectionStrategy {

    override val priority = 1

    override suspend fun selectProvider(context: SwapSelectionContext): SwapProvider? {

        val provider = swapQuoteRepository
            .resolveProvider(context.srcToken, context.dstToken) ?: return null

        return switchToKyberIfNecessary(provider, context.srcToken, context.dstToken, context.value)
    }


    private suspend fun switchToKyberIfNecessary(
        provider: SwapProvider,
        srcToken: Coin,
        dstToken: Coin,
        value: TokenValue,
    ): SwapProvider {
        if (provider != SwapProvider.THORCHAIN ||
            isErc20Swap(src = srcToken, dst = dstToken).not()
        )
            return provider

        val tokenValueInDollar = convertTokenValueToFiat(
            srcToken, value, AppCurrency.USD
        )

        return if (tokenValueInDollar.value < BigDecimal.valueOf(AMOUNT_FOR_THORCHAIN_OR_KYBER))
            SwapProvider.KYBER
        else provider
    }

    private fun isErc20Swap(src: Coin, dst: Coin) =
        src.chain.standard == TokenStandard.EVM
                && dst.chain.standard == TokenStandard.EVM
                && src.chain == dst.chain && !src.isNativeToken


    companion object {
        private const val AMOUNT_FOR_THORCHAIN_OR_KYBER = 100L
    }
}