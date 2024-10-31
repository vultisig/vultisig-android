package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import javax.inject.Inject

interface SearchEvmTokenUseCase : suspend (String, String) -> CoinAndPrice?

internal class SearchEvmTokenUseCaseImpl @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
) : SearchEvmTokenUseCase {

    override suspend operator fun invoke(chainId: String, contractAddress: String): CoinAndPrice? {
        val searchedToken = tokenRepository.getTokenByContract(
            chainId, contractAddress
        ) ?: return null
        val rawPrice = calculateEvmPrice(chainId, searchedToken.contractAddress)
        return CoinAndPrice(searchedToken, rawPrice)
    }

    private suspend fun calculateEvmPrice(
        chainId: String,
        contractAddress: String
    ) = tokenPriceRepository.getPriceByContactAddress(
        chainId,
        contractAddress
    )
}