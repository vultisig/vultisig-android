package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.api.errors.SwapKitError
import com.vultisig.wallet.data.api.swapAggregators.requireSwapKitEvmDeposit
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapQuote
import com.vultisig.wallet.data.models.SwapKitSwapPayloadJson
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.swap.SwapKitQuoteSource
import com.vultisig.wallet.data.repositories.swap.SwapPoolEligibilityRepository
import com.vultisig.wallet.data.repositories.swap.SwapProviderTable
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.repositories.swap.SwapQuoteSource
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

interface SwapQuoteRepository {

    suspend fun getQuote(provider: SwapProvider, request: SwapQuoteRequest): SwapQuoteResult

    /**
     * Re-fetch only the SwapKit source-chain inbound fee via `POST /v3/quote`, without firing the
     * `POST /v3/swap` call [getQuote] would. Display-only: lets the join flow surface a non-zero
     * swap fee without minting a throwaway swap route per cosigner.
     */
    suspend fun getSwapKitInboundFee(request: SwapQuoteRequest): TokenValue

    fun getEligibleProviders(srcToken: Coin, dstToken: Coin): List<SwapProvider>

    /**
     * Pre-warms the live THORChain / MayaChain pool eligibility cache so the first synchronous
     * [getEligibleProviders] read (run the moment a pair is selected) sees freshly fetched routes
     * instead of only the static fallback. Safe to call repeatedly; a fresh-enough cache no-ops.
     */
    suspend fun refreshSwapEligibility()

    /**
     * Increments once, from 0 to 1, when the live pool eligibility first populates after a cold
     * start, so a pair selected before [refreshSwapEligibility] landed can be re-evaluated.
     */
    val swapEligibilityVersion: StateFlow<Int>
}

internal class SwapQuoteRepositoryImpl
@Inject
constructor(
    private val sources: Map<SwapProvider, @JvmSuppressWildcards SwapQuoteSource>,
    private val swapKitQuoteSource: SwapKitQuoteSource,
    private val providerTable: SwapProviderTable,
    private val poolEligibility: SwapPoolEligibilityRepository,
) : SwapQuoteRepository {

    override suspend fun getQuote(
        provider: SwapProvider,
        request: SwapQuoteRequest,
    ): SwapQuoteResult {
        val source = sources[provider] ?: error("No SwapQuoteSource registered for $provider")
        val result = source.fetch(request)
        if (provider != SwapProvider.SWAPKIT) return result
        when (result) {
            is SwapQuoteResult.Evm ->
                if (request.srcToken.chain.standard == TokenStandard.EVM) {
                    try {
                        result.data.tx.requireSwapKitEvmDeposit(request.srcToken, request.tokenValue.value)
                    } catch (e: IllegalArgumentException) {
                        throw SwapKitError.UnableToBuildTransaction
                    }
                }
            is SwapQuoteResult.Native -> {
                val quote = result.quote as? SwapQuote.SwapKit
                if (quote != null && !SwapKitSwapPayloadJson.isSignableTxType(quote.data.txType, request.srcToken.chain)) {
                    throw SwapKitError.UnsupportedTxType(quote.data.txType)
                }
            }
        }
        return result
    }

    override suspend fun getSwapKitInboundFee(request: SwapQuoteRequest): TokenValue =
        swapKitQuoteSource.fetchInboundFee(request)

    override fun getEligibleProviders(srcToken: Coin, dstToken: Coin): List<SwapProvider> =
        providerTable.eligibleProvidersFor(srcToken, dstToken)

    override suspend fun refreshSwapEligibility() = poolEligibility.refresh()

    override val swapEligibilityVersion: StateFlow<Int>
        get() = poolEligibility.eligibilityVersion
}
