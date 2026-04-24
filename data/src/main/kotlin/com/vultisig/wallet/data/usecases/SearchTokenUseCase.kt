package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.TokenStandard.EVM
import com.vultisig.wallet.data.models.TokenStandard.SOL
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import java.math.BigDecimal
import javax.inject.Inject
import kotlinx.coroutines.flow.first

data class CoinAndPrice(val coin: Coin, val price: BigDecimal)

data class CoinAndFiatValue(val coin: Coin, val fiatValue: FiatValue)

interface SearchTokenUseCase : suspend (String, String) -> CoinAndFiatValue?

internal class SearchTokenUseCaseImpl
@Inject
constructor(
    private val appCurrencyRepository: AppCurrencyRepository,
    private val searchEvmToken: SearchEvmTokenUseCase,
    private val searchSolToken: SearchSolTokenUseCase,
    private val searchKujiToken: SearchKujiraTokenUseCase,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : SearchTokenUseCase {
    override suspend fun invoke(chainId: String, contractAddress: String): CoinAndFiatValue? {
        val chain = Chain.fromRaw(chainId)
        val address = contractAddress.trim()
        if (!isContractAddressValid(chain, address)) return null

        val searchedToken =
            when {
                chain.standard == EVM -> searchEvmToken(chainId, address)
                chain.standard == SOL -> searchSolToken(address)
                chain == Chain.Kujira -> searchKujiToken(address)
                else -> null
            } ?: return null

        if (!searchedToken.coin.hasSaneMetadata()) return null

        val currency = appCurrencyRepository.currency.first()
        val tokenFiatValue = FiatValue(searchedToken.price, currency.ticker)
        return CoinAndFiatValue(searchedToken.coin, tokenFiatValue)
    }

    private fun isContractAddressValid(chain: Chain, address: String): Boolean {
        if (address.isEmpty()) return false
        val candidate = canonicalize(chain, address)
        return chainAccountAddressRepository.isValid(chain, candidate)
    }

    // Kujira factory denoms take the form `factory/<creator-address>/<sub-denom>`;
    // the creator segment is what WalletCore can validate as bech32.
    private fun canonicalize(chain: Chain, address: String): String =
        if (chain == Chain.Kujira && address.startsWith(KUJIRA_FACTORY_PREFIX)) {
            address.substringAfter('/').substringBefore('/')
        } else {
            address
        }

    private fun Coin.hasSaneMetadata(): Boolean = ticker.isNotBlank() && decimal in 0..MAX_DECIMALS

    private companion object {
        const val KUJIRA_FACTORY_PREFIX = "factory/"
        const val MAX_DECIMALS = 30
    }
}
