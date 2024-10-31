package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.SplTokenRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import javax.inject.Inject

internal interface SearchSolTokenUseCase : suspend (String) -> CoinAndPrice?

internal class SearchSolTokenUseCaseImpl @Inject constructor(
    private val splTokenRepository: SplTokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
) : SearchSolTokenUseCase {

    override suspend operator fun invoke(contractAddress: String): CoinAndPrice? {
        val searchedToken = splTokenRepository.getTokenByContract(
            contractAddress
        ) ?: return null
        val rawPrice = calculateSolPrice(searchedToken.priceProviderID)
        return CoinAndPrice(searchedToken, rawPrice)
    }

    private suspend fun calculateSolPrice(
        priceProviderId: String
    ) = tokenPriceRepository.getPriceByPriceProviderId(
        priceProviderId
    )
}