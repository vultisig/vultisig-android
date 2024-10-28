package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.SplTokenRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

interface SearchTokenUseCase : suspend (String, String) -> Pair<Coin, FiatValue>?

internal class SearchTokenUseCaseImpl @Inject constructor(
    private val tokenRepository: TokenRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val splTokenRepository: SplTokenRepository,
) : SearchTokenUseCase {
    override suspend fun invoke(
        chainId: String,
        contractAddress: String
    ): Pair<Coin, FiatValue>? {
        val chain = Chain.fromRaw(chainId)
        val searchedToken = when (chain) {
            Chain.Ethereum -> tokenRepository
                .getTokenByContract(
                    chainId, contractAddress
                )

            Chain.Solana -> splTokenRepository
                .getTokenByContract(contractAddress)

            else -> null
        } ?: return null

        val rawPrice = calculatePrice(chainId, searchedToken.contractAddress)
        val currency = appCurrencyRepository.currency.first()
        val tokenFiatValue = FiatValue(
            rawPrice,
            currency.ticker
        )
        return Pair(searchedToken, tokenFiatValue)
    }

    private suspend fun calculatePrice(
        chainId: String,
        contractAddress: String
    ) = tokenPriceRepository.getPriceByContactAddress(
        chainId,
        contractAddress
    )
}