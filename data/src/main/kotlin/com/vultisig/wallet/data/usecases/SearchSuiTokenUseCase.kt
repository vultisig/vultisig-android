package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.chains.SuiCoinMetadata
import com.vultisig.wallet.data.crypto.SuiHelper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

internal interface SearchSuiTokenUseCase : suspend (String) -> CoinAndPrice?

internal class SearchSuiTokenUseCaseImpl
@Inject
constructor(private val suiApi: SuiApi, private val tokenPriceRepository: TokenPriceRepository) :
    SearchSuiTokenUseCase {

    override suspend operator fun invoke(coinType: String): CoinAndPrice? {
        curatedCoin(coinType)?.let { coin ->
            return CoinAndPrice(
                coin,
                tokenPriceRepository.getPriceByPriceProviderId(coin.priceProviderID),
            )
        }

        val metadata = fetchMetadata(coinType) ?: return null
        val coin =
            Coin(
                chain = Chain.Sui,
                ticker = metadata.symbol.trim(),
                logo = metadata.iconUrl.orEmpty(),
                address = "",
                decimal = metadata.decimals,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = coinType,
                isNativeToken = false,
            )
        return CoinAndPrice(coin, BigDecimal.ZERO)
    }

    /**
     * Mirrors [SuiTokenFinder]'s handling of the same call: a bad node response or transient
     * network failure must surface as "not found" rather than crash the custom-token screen, the
     * same contract [SearchTerraTokenUseCase] gives its own RPC reads.
     */
    private suspend fun fetchMetadata(coinType: String): SuiCoinMetadata? =
        try {
            suiApi.getCoinMetadata(coinType)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Sui coin metadata fetch failed for %s", coinType)
            null
        }

    /**
     * The curated [Coins] catalog entry for [coinType], matched through
     * [SuiHelper.isSameSuiCoinType] rather than a raw string compare — a node may return a coin
     * type's package address zero-padded where the catalog stores it short. Preferred over the
     * on-chain [SuiApi.getCoinMetadata] read because the catalog carries a hand-verified logo and
     * `priceProviderID`; on-chain metadata frequently omits `iconUrl`, which is what left custom-
     * added catalog tokens like DEEP without an icon (vultisig-android#5507 follow-up).
     */
    private fun curatedCoin(coinType: String): Coin? =
        Coins.coins[Chain.Sui]?.firstOrNull {
            !it.isNativeToken && SuiHelper.isSameSuiCoinType(it.contractAddress, coinType)
        }
}
