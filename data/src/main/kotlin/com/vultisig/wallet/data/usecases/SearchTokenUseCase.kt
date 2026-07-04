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
    private val searchTerraToken: SearchTerraTokenUseCase,
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
            chain == Chain.Terra || chain == Chain.TerraClassic -> searchTerraToken(chain, address)
            else -> null
        }

    private suspend fun CoinAndPrice.withFiatValue(): CoinAndFiatValue {
        val currency = appCurrencyRepository.currency.first()
        return CoinAndFiatValue(coin, FiatValue(price, currency.ticker))
    }

    private fun String.isValidAddressOn(chain: Chain): Boolean =
        when (chain) {
            Chain.Terra,
            Chain.TerraClassic -> isCw20ContractAddressShape()
            else ->
                isNotEmpty() &&
                    chainAccountAddressRepository.isValid(chain, canonicalizedFor(chain))
        }

    private fun String.canonicalizedFor(chain: Chain): String =
        if (chain == Chain.Kujira && startsWith(KUJIRA_FACTORY_PREFIX)) {
            substringAfter('/').substringBefore('/')
        } else this

    /**
     * Bech32 *shape* check for a Terra CW20 contract address, mirroring the SDK's
     * `isCosmosWasmTokenId` — WalletCore address validation isn't specified for 32-byte contract
     * payloads, so it can't be used here. Contract addresses can be 20-byte (Terra Classic
     * pre-migration contracts, indistinguishable from wallet addresses) or 32-byte; only the LCD
     * query itself can tell a contract from a wallet address. `ibc/…` and `factory/…` bank denoms
     * are rejected.
     */
    private fun String.isCw20ContractAddressShape(): Boolean {
        if (!startsWith(TERRA_CONTRACT_PREFIX)) return false
        val payload = removePrefix(TERRA_CONTRACT_PREFIX)
        return payload.length in 20..80 && payload.all { it in 'a'..'z' || it in '0'..'9' }
    }

    private fun Coin.hasSaneMetadata(): Boolean = ticker.isNotBlank() && decimal in 0..MAX_DECIMALS

    private companion object {
        const val KUJIRA_FACTORY_PREFIX = "factory/"
        const val TERRA_CONTRACT_PREFIX = "terra1"
        const val MAX_DECIMALS = 30
    }
}
