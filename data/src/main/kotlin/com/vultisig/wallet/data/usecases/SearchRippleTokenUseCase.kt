package com.vultisig.wallet.data.usecases

import com.vultisig.wallet.data.api.RippleApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.RIPPLE_TOKEN_DECIMALS
import com.vultisig.wallet.data.models.isSignableRippleCurrencyCode
import com.vultisig.wallet.data.models.parseRippleTokenIdentity
import com.vultisig.wallet.data.models.rippleCurrencyTicker
import com.vultisig.wallet.data.models.rippleTokenContractAddress
import com.vultisig.wallet.data.models.toRippleCurrencyCodeOrNull
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import timber.log.Timber

internal interface SearchRippleTokenUseCase : suspend (String) -> CoinAndPrice?

internal class SearchRippleTokenUseCaseImpl
@Inject
constructor(
    private val rippleApi: RippleApi,
    private val tokenPriceRepository: TokenPriceRepository,
) : SearchRippleTokenUseCase {

    override suspend operator fun invoke(tokenId: String): CoinAndPrice? {
        val identity = parseRippleTokenIdentity(tokenId) ?: return null
        val currency = toRippleCurrencyCodeOrNull(identity.currency) ?: return null
        // WalletCore re-cases a 3-byte code, so the signer refuses one it would alter.
        if (!isSignableRippleCurrencyCode(currency)) return null
        val contractAddress = rippleTokenContractAddress(currency, identity.issuer)
        curatedCoin(contractAddress)?.let { coin ->
            return CoinAndPrice(
                coin,
                tokenPriceRepository.getPriceByPriceProviderId(coin.priceProviderID),
            )
        }

        // Obligations name every currency the issuer put on the ledger, so one membership test
        // settles both halves of the pair.
        if (currency !in issuedCurrencies(identity.issuer)) return null

        val coin =
            Coin(
                chain = Chain.Ripple,
                ticker = rippleCurrencyTicker(currency),
                logo = "",
                address = "",
                decimal = RIPPLE_TOKEN_DECIMALS,
                hexPublicKey = "",
                priceProviderID = "",
                contractAddress = contractAddress,
                isNativeToken = false,
            )
        return CoinAndPrice(coin, BigDecimal.ZERO)
    }

    private suspend fun issuedCurrencies(issuer: String): Set<String> =
        try {
            rippleApi.fetchIssuedCurrencies(issuer)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "XRPL gateway balances fetch failed for %s", issuer)
            emptySet()
        }

    /** Preferred over a synthesized coin: the catalog entry carries a logo and price provider. */
    private fun curatedCoin(contractAddress: String): Coin? =
        Coins.coins[Chain.Ripple]?.firstOrNull {
            !it.isNativeToken && it.contractAddress == contractAddress
        }
}
