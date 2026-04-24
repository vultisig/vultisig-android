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
        return contractAddress
            .trim()
            .takeIf { it.isValidAddressOn(chain) }
            ?.let { address -> dispatch(chain, chainId, address) }
            ?.takeIf { it.coin.hasSaneMetadata() }
            ?.withFiatValue()
    }

    private suspend fun dispatch(chain: Chain, chainId: String, address: String): CoinAndPrice? =
        when {
            chain.standard == EVM -> searchEvmToken(chainId, address)
            chain.standard == SOL -> searchSolToken(address)
            chain == Chain.Kujira -> searchKujiToken(address)
            else -> null
        }

    private suspend fun CoinAndPrice.withFiatValue(): CoinAndFiatValue {
        val currency = appCurrencyRepository.currency.first()
        return CoinAndFiatValue(coin, FiatValue(price, currency.ticker))
    }

    private fun String.isValidAddressOn(chain: Chain): Boolean =
        isNotEmpty() && chainAccountAddressRepository.isValid(chain, canonicalizedFor(chain))

    private fun String.canonicalizedFor(chain: Chain): String =
        if (chain == Chain.Kujira && startsWith(KUJIRA_FACTORY_PREFIX)) {
            substringAfter('/').substringBefore('/')
        } else this

    private fun Coin.hasSaneMetadata(): Boolean = ticker.isNotBlank() && decimal in 0..MAX_DECIMALS

    private companion object {
        const val KUJIRA_FACTORY_PREFIX = "factory/"
        const val MAX_DECIMALS = 30
    }
}
