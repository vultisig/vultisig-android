package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.chains.ton.TonJettonMetadata
import com.vultisig.wallet.data.api.chains.ton.tonUserFriendlyAddress
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.utils.NetworkException
import java.io.IOException
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException
import timber.log.Timber

internal interface SearchTonTokenUseCase : suspend (String) -> CoinAndPrice?

internal class SearchTonTokenUseCaseImpl
@Inject
constructor(private val tonApi: TonApi, private val tokenPriceRepository: TokenPriceRepository) :
    SearchTonTokenUseCase {

    override suspend operator fun invoke(masterAddress: String): CoinAndPrice? {
        curatedCoin(masterAddress)?.let { coin ->
            return CoinAndPrice(
                coin,
                tokenPriceRepository.getPriceByPriceProviderId(coin.priceProviderID),
            )
        }

        val metadata = fetchMetadata(masterAddress) ?: return null
        val coin =
            Coin(
                chain = Chain.Ton,
                ticker = metadata.ticker,
                logo = metadata.logo.orEmpty(),
                address = "",
                decimal = metadata.decimals,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = masterAddress,
                isNativeToken = false,
            )
        return CoinAndPrice(coin, BigDecimal.ZERO)
    }

    private suspend fun fetchMetadata(masterAddress: String): TonJettonMetadata? =
        try {
            tonApi.getJettonMetadata(masterAddress)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NetworkException) {
            Timber.d(e, "Ton jetton metadata fetch failed for %s", masterAddress)
            null
        } catch (e: IOException) {
            Timber.d(e, "Ton jetton metadata fetch failed for %s", masterAddress)
            null
        } catch (e: SerializationException) {
            Timber.d(e, "Ton jetton metadata fetch failed for %s", masterAddress)
            null
        }

    /**
     * Matched through [tonUserFriendlyAddress] rather than a raw string compare — a master can be
     * quoted in bounceable `EQ…`, non-bounceable `UQ…`, or raw `0:hex` form.
     */
    private fun curatedCoin(masterAddress: String): Coin? {
        val canonical = tonUserFriendlyAddress(masterAddress) ?: masterAddress
        return Coins.coins[Chain.Ton]?.firstOrNull {
            !it.isNativeToken &&
                (tonUserFriendlyAddress(it.contractAddress) ?: it.contractAddress) == canonical
        }
    }
}
