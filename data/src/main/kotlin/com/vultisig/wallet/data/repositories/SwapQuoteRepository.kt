package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.repositories.swap.SwapKitQuoteSource
import com.vultisig.wallet.data.repositories.swap.SwapProviderTable
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.repositories.swap.SwapQuoteSource
import javax.inject.Inject

interface SwapQuoteRepository {

    suspend fun getQuote(provider: SwapProvider, request: SwapQuoteRequest): SwapQuoteResult

    /**
     * Re-fetch only the SwapKit source-chain inbound fee via `POST /v3/quote`, without firing the
     * `POST /v3/swap` call [getQuote] would. Display-only: lets the join flow surface a non-zero
     * swap fee without minting a throwaway swap route per cosigner.
     */
    suspend fun getSwapKitInboundFee(request: SwapQuoteRequest): TokenValue

    fun getEligibleProviders(srcToken: Coin, dstToken: Coin): List<SwapProvider>
}

internal class SwapQuoteRepositoryImpl
@Inject
constructor(
    private val sources: Map<SwapProvider, @JvmSuppressWildcards SwapQuoteSource>,
    private val swapKitQuoteSource: SwapKitQuoteSource,
    private val providerTable: SwapProviderTable,
) : SwapQuoteRepository {

    override suspend fun getQuote(
        provider: SwapProvider,
        request: SwapQuoteRequest,
    ): SwapQuoteResult {
        val source = sources[provider] ?: error("No SwapQuoteSource registered for $provider")
        return source.fetch(request)
    }

    override suspend fun getSwapKitInboundFee(request: SwapQuoteRequest): TokenValue =
        swapKitQuoteSource.fetchInboundFee(request)

    override fun getEligibleProviders(srcToken: Coin, dstToken: Coin): List<SwapProvider> =
        providerTable.eligibleProvidersFor(srcToken, dstToken)
}
