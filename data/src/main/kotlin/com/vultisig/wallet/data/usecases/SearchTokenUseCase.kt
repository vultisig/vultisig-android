package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenStandard.EVM
import com.vultisig.wallet.data.models.TokenStandard.SOL
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import javax.inject.Inject

data class CoinAndPrice(
    val coin: Coin,
    val price: BigDecimal
)

data class CoinAndFiatValue(
    val coin: Coin,
    val fiatValue: FiatValue
)

interface SearchTokenUseCase : suspend (String, String) -> CoinAndFiatValue?

internal class SearchTokenUseCaseImpl @Inject constructor(
    private val appCurrencyRepository: AppCurrencyRepository,
    private val searchEvmToken: SearchEvmTokenUseCase,
    private val searchSolToken: SearchSolTokenUseCase,
    private val searchKujiToken: SearchKujiraTokenUseCase,
) : SearchTokenUseCase {
    override suspend fun invoke(
        chainId: String,
        contractAddress: String
    ): CoinAndFiatValue? {
        val chain = Chain.fromRaw(chainId)
        val searchedToken = when {
            chain.standard == EVM -> searchEvmToken(chainId, contractAddress)
            chain.standard == SOL -> searchSolToken(contractAddress)
            chain == Chain.Kujira -> searchKujiToken(contractAddress)
            else -> error("search token not supported for $chain")
        } ?: return null

        val rawPrice = searchedToken.price

        val currency = appCurrencyRepository.currency.first()
        val tokenFiatValue = FiatValue(
            rawPrice,
            currency.ticker
        )
        return CoinAndFiatValue(searchedToken.coin, tokenFiatValue)
    }
}