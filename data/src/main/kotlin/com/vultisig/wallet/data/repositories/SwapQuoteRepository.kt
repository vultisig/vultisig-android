package com.vultisig.wallet.data.repositories

import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.repositories.swap.SwapProviderTable
import com.vultisig.wallet.data.repositories.swap.SwapQuoteRequest
import com.vultisig.wallet.data.repositories.swap.SwapQuoteResult
import com.vultisig.wallet.data.repositories.swap.SwapQuoteSource
import javax.inject.Inject

interface SwapQuoteRepository {

    suspend fun getQuote(provider: SwapProvider, request: SwapQuoteRequest): SwapQuoteResult

    fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider?
}

internal class SwapQuoteRepositoryImpl
@Inject
constructor(
    private val sources: Map<SwapProvider, @JvmSuppressWildcards SwapQuoteSource>,
    private val providerTable: SwapProviderTable,
) : SwapQuoteRepository {

    override suspend fun getQuote(
        provider: SwapProvider,
        request: SwapQuoteRequest,
    ): SwapQuoteResult {
        val source = sources[provider] ?: error("No SwapQuoteSource registered for $provider")
        return source.fetch(request)
    }

    override fun resolveProvider(srcToken: Coin, dstToken: Coin): SwapProvider? =
        providerTable.providerFor(srcToken, dstToken)
}
