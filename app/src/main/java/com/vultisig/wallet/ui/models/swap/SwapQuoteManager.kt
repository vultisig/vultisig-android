package com.vultisig.wallet.ui.models.swap

import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapQuote.Companion.expiredAfter
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.usecases.ConvertTokenToToken
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.SearchTokenUseCase
import com.vultisig.wallet.data.utils.thorswapMultiplier
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.send.SendSrc
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.datetime.Clock
import timber.log.Timber

internal data class QuoteFetchResult(
    val quote: SwapQuote,
    val provider: SwapProvider,
    val providerUiText: UiText,
    val srcFiatValueText: String,
    val estimatedDstTokenValue: String,
    val estimatedDstFiatValue: String,
    val feeText: String,
    val swapFeeFiat: FiatValue,
)

internal class SwapQuoteManager
@Inject
constructor(
    private val swapQuoteRepository: SwapQuoteRepository,
    private val tokenRepository: TokenRepository,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
    private val searchToken: SearchTokenUseCase,
    private val convertTokenToTokenUseCase: ConvertTokenToToken,
) {

    private val quoteCache = QuoteCache()

    suspend fun fetchQuote(
        provider: SwapProvider,
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        currency: AppCurrency,
        vultBPSDiscount: Int?,
        referral: String?,
        amount: BigDecimal,
    ): QuoteFetchResult {
        val srcNativeToken = tokenRepository.getNativeToken(srcToken.chain.id)

        val srcFiatValue = convertTokenValueToFiat(srcToken, tokenValue, currency)
        val srcFiatValueText = fiatValueToString(srcFiatValue)

        val (quote, providerText) =
            when (provider) {
                SwapProvider.MAYA,
                SwapProvider.THORCHAIN ->
                    fetchThorMayaQuote(
                        provider,
                        src,
                        dst,
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        referral,
                        amount,
                    )

                SwapProvider.KYBER ->
                    fetchKyberQuote(
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        provider,
                        srcNativeToken,
                    )

                SwapProvider.ONEINCH ->
                    fetchOneInchQuote(
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        provider,
                        srcNativeToken,
                    )

                SwapProvider.LIFI,
                SwapProvider.JUPITER ->
                    fetchLiFiJupiterQuote(
                        provider,
                        src,
                        dst,
                        srcToken,
                        dstToken,
                        srcTokenValue,
                        tokenValue,
                        vultBPSDiscount,
                        srcNativeToken,
                    )
            }

        val feeCoin =
            when (provider) {
                SwapProvider.MAYA,
                SwapProvider.THORCHAIN,
                SwapProvider.LIFI -> dstToken
                else -> srcNativeToken
            }

        val fiatFees = convertTokenValueToFiat(feeCoin, quote.fees, currency)
        val estimatedDstTokenValue = mapTokenValueToDecimalUiString(quote.expectedDstValue)
        val estimatedDstFiatValue =
            convertTokenValueToFiat(dstToken, quote.expectedDstValue, currency)

        return QuoteFetchResult(
            quote = quote,
            provider = provider,
            providerUiText = providerText,
            srcFiatValueText = srcFiatValueText,
            estimatedDstTokenValue = estimatedDstTokenValue,
            estimatedDstFiatValue = fiatValueToString(estimatedDstFiatValue),
            feeText = fiatValueToString(fiatFees),
            swapFeeFiat = fiatFees,
        )
    }

    private suspend fun fetchThorMayaQuote(
        provider: SwapProvider,
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        referral: String?,
        amount: BigDecimal,
    ): Pair<SwapQuote, UiText> {
        val isAffiliate = true
        val (quote, recommendedMinAmountToken) =
            if (provider == SwapProvider.MAYA) {
                val mayaSwapQuote =
                    getCachedQuoteOrFetch(
                        srcToken.id,
                        dstToken.id,
                        srcTokenValue,
                        SwapProvider.MAYA,
                    ) {
                        (swapQuoteRepository.getQuote(
                                SwapProvider.MAYA,
                                SwapQuoteRequest(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    dstAddress = dst.address.address,
                                    isAffiliate = isAffiliate,
                                    bpsDiscount = vultBPSDiscount ?: 0,
                                    referralCode = referral.orEmpty(),
                                ),
                            ) as SwapQuoteResult.Native)
                            .quote
                    }
                        as SwapQuote.MayaChain
                mayaSwapQuote to mayaSwapQuote.recommendedMinTokenValue
            } else {
                val thorSwapQuote =
                    getCachedQuoteOrFetch(
                        srcToken.id,
                        dstToken.id,
                        srcTokenValue,
                        SwapProvider.THORCHAIN,
                    ) {
                        (swapQuoteRepository.getQuote(
                                SwapProvider.THORCHAIN,
                                SwapQuoteRequest(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    dstAddress = dst.address.address,
                                    referralCode = referral.orEmpty(),
                                    bpsDiscount = vultBPSDiscount ?: 0,
                                ),
                            ) as SwapQuoteResult.Native)
                            .quote
                    }
                        as SwapQuote.ThorChain
                thorSwapQuote to thorSwapQuote.recommendedMinTokenValue
            }

        val recommendedMinAmountTokenString =
            mapTokenValueToDecimalUiString(recommendedMinAmountToken)
        if (amount < recommendedMinAmountToken.decimal) {
            throw SwapException.SmallSwapAmount(recommendedMinAmountTokenString)
        }

        val providerText =
            if (provider == SwapProvider.MAYA) R.string.swap_form_provider_mayachain.asUiText()
            else R.string.swap_form_provider_thorchain.asUiText()

        return quote to providerText
    }

    private suspend fun fetchKyberQuote(
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        provider: SwapProvider,
        srcNativeToken: Coin,
    ): Pair<SwapQuote, UiText> {
        val swapQuote =
            getCachedQuoteOrFetch(srcToken.id, dstToken.id, srcTokenValue, SwapProvider.KYBER) {
                val apiQuote =
                    (swapQuoteRepository.getQuote(
                            SwapProvider.KYBER,
                            SwapQuoteRequest(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                tokenValue = tokenValue,
                                affiliateBps =
                                    maxOf(0, KYBER_AFFILIATE_FEE_BPS - (vultBPSDiscount ?: 0)),
                            ),
                        ) as SwapQuoteResult.Evm)
                        .data
                val expectedDstValue =
                    TokenValue(value = apiQuote.dstAmount.toBigInteger(), token = dstToken)
                val gasFees =
                    apiQuote.tx.gasPrice.toBigInteger() *
                        (apiQuote.tx.gas.takeIf { it != 0L } ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT)
                            .toBigInteger()
                val (feeAmount, feeCoin) =
                    resolveSwapFee(
                        apiQuote.tx.swapFeeTokenContract,
                        apiQuote.tx.swapFee,
                        srcNativeToken,
                        gasFees,
                    )
                val updatedTx = apiQuote.tx.copy(swapFee = feeAmount.toString())
                val tokenFees = TokenValue(value = feeAmount, token = feeCoin)
                SwapQuote.OneInch(
                    expectedDstValue = expectedDstValue,
                    fees = tokenFees,
                    data = apiQuote.copy(tx = updatedTx),
                    expiredAt = Clock.System.now() + expiredAfter,
                    provider = provider.getSwapProviderId(),
                )
            }
        return swapQuote to R.string.swap_for_provider_kyber.asUiText()
    }

    private suspend fun fetchOneInchQuote(
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        provider: SwapProvider,
        srcNativeToken: Coin,
    ): Pair<SwapQuote, UiText> {
        val isAffiliate = true
        val swapQuote =
            getCachedQuoteOrFetch(srcToken.id, dstToken.id, srcTokenValue, SwapProvider.ONEINCH) {
                val apiQuote =
                    (swapQuoteRepository.getQuote(
                            SwapProvider.ONEINCH,
                            SwapQuoteRequest(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                tokenValue = tokenValue,
                                isAffiliate = isAffiliate,
                                bpsDiscount = vultBPSDiscount ?: 0,
                            ),
                        ) as SwapQuoteResult.Evm)
                        .data
                val expectedDstValue =
                    TokenValue(value = apiQuote.dstAmount.toBigInteger(), token = dstToken)
                val tokenFees =
                    TokenValue(
                        value =
                            apiQuote.tx.gasPrice.toBigInteger() *
                                (apiQuote.tx.gas.takeIf { it != 0L }
                                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT)
                                    .toBigInteger(),
                        token = srcNativeToken,
                    )
                SwapQuote.OneInch(
                    expectedDstValue = expectedDstValue,
                    fees = tokenFees,
                    data = apiQuote,
                    expiredAt = Clock.System.now() + expiredAfter,
                    provider = provider.getSwapProviderId(),
                )
            }
        return swapQuote to R.string.swap_for_provider_1inch.asUiText()
    }

    private suspend fun fetchLiFiJupiterQuote(
        provider: SwapProvider,
        src: SendSrc,
        dst: SendSrc,
        srcToken: Coin,
        dstToken: Coin,
        srcTokenValue: BigInteger,
        tokenValue: TokenValue,
        vultBPSDiscount: Int?,
        srcNativeToken: Coin,
    ): Pair<SwapQuote, UiText> {
        val swapQuote =
            getCachedQuoteOrFetch(srcToken.id, dstToken.id, srcTokenValue, provider) {
                val apiQuote =
                    if (provider == SwapProvider.LIFI)
                        (swapQuoteRepository.getQuote(
                                SwapProvider.LIFI,
                                SwapQuoteRequest(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    srcAddress = src.address.address,
                                    dstAddress = dst.address.address,
                                    bpsDiscount = vultBPSDiscount ?: 0,
                                ),
                            ) as SwapQuoteResult.Evm)
                            .data
                    else
                        (swapQuoteRepository.getQuote(
                                SwapProvider.JUPITER,
                                SwapQuoteRequest(
                                    srcToken = srcToken,
                                    dstToken = dstToken,
                                    tokenValue = tokenValue,
                                    srcAddress = src.address.address,
                                ),
                            ) as SwapQuoteResult.Evm)
                            .data
                val expectedDstValue =
                    TokenValue(value = apiQuote.dstAmount.toBigInteger(), token = dstToken)
                val (feeAmount, feeCoin) =
                    if (provider == SwapProvider.LIFI) {
                        val feeWei =
                            LiFiChainApi.integratorFeeAmount(
                                dstAmount = apiQuote.dstAmount.toBigInteger(),
                                bpsDiscount = vultBPSDiscount ?: 0,
                            )
                        Pair(feeWei, dstToken)
                    } else {
                        resolveSwapFee(
                            apiQuote.tx.swapFeeTokenContract,
                            apiQuote.tx.swapFee,
                            srcNativeToken,
                            apiQuote.tx.swapFee.toBigInteger(),
                        )
                    }
                val updatedTx = apiQuote.tx.copy(swapFee = feeAmount.toString())
                val tokenFees = TokenValue(value = feeAmount, token = feeCoin)
                SwapQuote.OneInch(
                    expectedDstValue = expectedDstValue,
                    fees = tokenFees,
                    data = apiQuote.copy(tx = updatedTx),
                    expiredAt = Clock.System.now() + expiredAfter,
                    provider = provider.getSwapProviderId(),
                )
            }
        val providerText =
            if (provider == SwapProvider.LIFI) {
                R.string.swap_for_provider_li_fi.asUiText()
            } else {
                R.string.swap_for_provider_jupiter.asUiText()
            }
        return swapQuote to providerText
    }

    private suspend fun resolveSwapFee(
        swapFeeTokenContract: String,
        swapFeeRaw: String,
        srcNativeToken: Coin,
        fallbackFee: BigInteger,
    ): Pair<BigInteger, Coin> =
        try {
            if (swapFeeTokenContract.isNotEmpty()) {
                val chainId = srcNativeToken.chain.id
                val amount = swapFeeRaw.toBigInteger()
                val coinAndFiatValue =
                    searchToken(chainId, swapFeeTokenContract) ?: error("Can't find token or price")
                val newNativeAmount =
                    convertTokenToTokenUseCase.convertTokenToToken(
                        amount,
                        coinAndFiatValue,
                        srcNativeToken,
                    )
                Pair(newNativeAmount, srcNativeToken)
            } else {
                Pair(fallbackFee, srcNativeToken)
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            Timber.e(t)
            Pair(fallbackFee, srcNativeToken)
        }

    fun cacheQuote(
        quote: SwapQuote,
        provider: SwapProvider,
        srcTokenId: String,
        dstTokenId: String,
        srcAmount: BigInteger,
    ) {
        quoteCache.put(srcTokenId, dstTokenId, srcAmount, provider, quote)
    }

    private suspend fun getCachedQuoteOrFetch(
        srcTokenId: String,
        dstTokenId: String,
        srcAmount: BigInteger,
        provider: SwapProvider,
        fetch: suspend () -> SwapQuote,
    ): SwapQuote {
        quoteCache.get(srcTokenId, dstTokenId, srcAmount, provider)?.let {
            return it
        }
        return fetch().also { fresh ->
            quoteCache.put(srcTokenId, dstTokenId, srcAmount, provider, fresh)
        }
    }

    fun mapSwapExceptionToFormError(
        e: SwapException,
        srcToken: Coin,
        selectedSrcTokenTitle: String?,
    ): UiText =
        when (e) {
            is SwapException.SwapIsNotSupported ->
                UiText.StringResource(R.string.swap_route_not_available)
            is SwapException.AmountCannotBeZero ->
                UiText.StringResource(R.string.swap_form_invalid_amount)
            is SwapException.SameAssets ->
                UiText.StringResource(R.string.swap_screen_same_asset_error_message)
            is SwapException.UnkownSwapError ->
                UiText.StringResource(R.string.swap_error_quote_failed)
            is SwapException.HighPriceImpact ->
                UiText.StringResource(R.string.swap_error_high_price_impact)
            is SwapException.InsufficentSwapAmount ->
                UiText.StringResource(R.string.swap_error_amount_too_low)
            is SwapException.SwapRouteNotAvailable ->
                UiText.StringResource(R.string.swap_route_not_available)
            is SwapException.TimeOut -> UiText.StringResource(R.string.swap_error_time_out)
            is SwapException.NetworkConnection ->
                UiText.StringResource(R.string.network_connection_lost)
            is SwapException.SmallSwapAmount -> {
                val rawAmount =
                    e.message?.let { msg ->
                        Regex("""recommended_min_amount_in:\s*(\d+)""")
                            .find(msg)
                            ?.groupValues
                            ?.get(1)
                            ?.toLongOrNull()
                    }
                if (rawAmount != null) {
                    val multiplier = srcToken.thorswapMultiplier
                    val tokenAmount =
                        BigDecimal(rawAmount)
                            .divide(multiplier)
                            .movePointRight(srcToken.decimal)
                            .toBigInteger()
                    val formattedAmount =
                        mapTokenValueToDecimalUiString(
                            TokenValue(value = tokenAmount, token = srcToken)
                        )
                    UiText.FormattedText(
                        R.string.swap_form_minimum_amount,
                        listOf(formattedAmount, selectedSrcTokenTitle ?: ""),
                    )
                } else if (e.message?.toDoubleOrNull() != null) {
                    UiText.FormattedText(
                        R.string.swap_form_minimum_amount,
                        listOf(e.message ?: "", selectedSrcTokenTitle ?: ""),
                    )
                } else {
                    e.message?.let { UiText.DynamicString(it) }
                        ?: UiText.StringResource(R.string.swap_error_amount_too_low)
                }
            }
            is SwapException.InsufficientFunds ->
                UiText.StringResource(R.string.swap_error_small_insufficient_funds)
            is SwapException.RateLimitExceeded ->
                UiText.StringResource(R.string.swap_error_rate_limit)
            is SwapException.AmountBelowDustThreshold ->
                UiText.StringResource(R.string.swap_error_amount_below_dust_threshold)
        }

    companion object {
        private const val KYBER_AFFILIATE_FEE_BPS = 50
    }
}

private class QuoteCache(private val maxSize: Int = MAX_SIZE) {

    private data class Key(
        val srcTokenId: String,
        val dstTokenId: String,
        val srcAmount: BigInteger,
        val provider: SwapProvider,
    )

    private val lock = Any()
    private val entries = linkedMapOf<Key, SwapQuote>()

    fun get(
        srcTokenId: String,
        dstTokenId: String,
        srcAmount: BigInteger,
        provider: SwapProvider,
    ): SwapQuote? =
        synchronized(lock) {
            val key = Key(srcTokenId, dstTokenId, srcAmount, provider)
            val quote = entries[key] ?: return null
            if (Clock.System.now() < quote.expiredAt) {
                quote
            } else {
                entries.remove(key)
                null
            }
        }

    fun put(
        srcTokenId: String,
        dstTokenId: String,
        srcAmount: BigInteger,
        provider: SwapProvider,
        quote: SwapQuote,
    ) =
        synchronized(lock) {
            entries[Key(srcTokenId, dstTokenId, srcAmount, provider)] = quote
            evict()
        }

    private fun evict() {
        val now = Clock.System.now()
        entries.entries.removeAll { now >= it.value.expiredAt }
        val iter = entries.entries.iterator()
        while (entries.size > maxSize && iter.hasNext()) {
            iter.next()
            iter.remove()
        }
    }

    companion object {
        private const val MAX_SIZE = 6
    }
}
