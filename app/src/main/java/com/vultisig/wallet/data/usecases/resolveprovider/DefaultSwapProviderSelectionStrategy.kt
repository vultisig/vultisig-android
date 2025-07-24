package com.vultisig.wallet.data.usecases.resolveprovider

import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import javax.inject.Inject

internal interface DefaultSwapProviderSelectionStrategy : SwapProviderSelectionStrategy


internal class DefaultSwapProviderSelectionStrategyImpl @Inject constructor(
    private val swapQuoteRepository: SwapQuoteRepository,
) : DefaultSwapProviderSelectionStrategy {

    override val priority = 0

    override suspend fun selectProvider(context: SwapSelectionContext) =
        swapQuoteRepository.resolveProvider(context.srcToken, context.dstToken)

}