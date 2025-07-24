package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import java.math.BigDecimal
import javax.inject.Inject


internal interface ResolveProviderUseCase : suspend (Coin, Coin, TokenValue, ) -> SwapProvider

internal class ResolveProviderUseCaseImpl @Inject constructor(
    private val swapQuoteRepository: SwapQuoteRepository,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
) : ResolveProviderUseCase {

    override suspend fun invoke(
        srcToken: Coin,
        dstToken: Coin,
        value: TokenValue,
    ): SwapProvider {
        val provider = swapQuoteRepository.resolveProvider(srcToken, dstToken)
            ?: throw SwapException.SwapIsNotSupported("Swap is not supported for this pair")

        return switchToKyberIfNecessary(provider, srcToken, dstToken, value)
    }


    private suspend fun switchToKyberIfNecessary(
        provider: SwapProvider,
        srcToken: Coin,
        dstToken: Coin,
        value: TokenValue,
    ): SwapProvider {
        if (provider != SwapProvider.THORCHAIN || isErc20Swap(srcToken, dstToken).not())
            return provider

        val tokenValueInDollar = convertTokenValueToFiat(
            srcToken,
            value,
            AppCurrency.USD
        )

        // If the swap amount is below $100, use Kyber as the provider; otherwise, use THORChain.
        return if (tokenValueInDollar.value < BigDecimal.valueOf(AMOUNT_FOR_THORCHAIN_OR_KYBER))
            SwapProvider.KYBER

        else provider
    }

    private fun isErc20Swap(src: Coin, dst: Coin): Boolean =
        src.chain.standard == TokenStandard.EVM
                && dst.chain.standard == TokenStandard.EVM
                && src.chain == dst.chain && !src.isNativeToken


    companion object {
        private const val AMOUNT_FOR_THORCHAIN_OR_KYBER = 100L
    }
}


