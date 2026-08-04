package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import java.math.BigDecimal
import javax.inject.Inject

internal interface SearchSuiTokenUseCase : suspend (String) -> CoinAndPrice?

internal class SearchSuiTokenUseCaseImpl @Inject constructor(private val suiApi: SuiApi) :
    SearchSuiTokenUseCase {

    override suspend operator fun invoke(coinType: String): CoinAndPrice? {
        val metadata = suiApi.getCoinMetadata(coinType) ?: return null
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
}
