package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

/**
 * Resolves a user-pasted CW20 contract address into a [Coin] for the custom-token flow on Terra and
 * Terra Classic.
 *
 * CW20 tokens are CosmWasm contracts, not bank denoms, so their metadata comes from the
 * `{"token_info":{}}` smart query on the chain's LCD rather than `denoms_metadata`. A curated
 * [Coins] catalog entry (matched by contract address) is preferred so known tokens keep their real
 * logo and price provider id; unknown tokens get an empty logo and no price id — the same behavior
 * as custom tokens on every other chain.
 */
internal interface SearchTerraTokenUseCase : suspend (Chain, String) -> CoinAndPrice?

internal class SearchTerraTokenUseCaseImpl
@Inject
constructor(private val cosmosApiFactory: CosmosApiFactory) : SearchTerraTokenUseCase {

    override suspend operator fun invoke(chain: Chain, contractAddress: String): CoinAndPrice? {
        val tokenInfo =
            try {
                cosmosApiFactory.createCosmosApi(chain).getCw20TokenInfo(contractAddress)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: return null

        val symbol = tokenInfo.symbol?.trim().orEmpty()
        val decimals = tokenInfo.decimals
        if (symbol.isEmpty() || decimals == null || decimals < 0) return null

        val coin =
            Coins.coins[chain]?.firstOrNull { it.contractAddress == contractAddress }
                ?: Coin(
                    chain = chain,
                    ticker = symbol,
                    logo = "",
                    address = "",
                    hexPublicKey = "",
                    decimal = decimals,
                    priceProviderID = "",
                    contractAddress = contractAddress,
                    isNativeToken = false,
                )
        return CoinAndPrice(coin, BigDecimal.ZERO)
    }
}
