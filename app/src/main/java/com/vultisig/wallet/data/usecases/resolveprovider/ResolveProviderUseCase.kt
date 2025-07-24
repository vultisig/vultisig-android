package com.vultisig.wallet.data.usecases.resolveprovider

import com.vultisig.wallet.data.models.SwapProvider
import javax.inject.Inject

internal interface ResolveProviderUseCase :
    suspend (SwapSelectionContext) -> SwapProvider?

internal class ResolveProviderUseCaseImpl @Inject constructor(
    defaultSwapProviderSelectionStrategy: DefaultSwapProviderSelectionStrategy,
    kyberSwapProviderSelectionStrategy: KyberSwapProviderSelectionStrategy,
) : ResolveProviderUseCase {

    private val swapProviderSelectionStrategies = listOf(
        defaultSwapProviderSelectionStrategy,
        kyberSwapProviderSelectionStrategy,
    )

    override suspend fun invoke(
        context: SwapSelectionContext,
    ): SwapProvider? {

        return swapProviderSelectionStrategies
            .maxBy(SwapProviderSelectionStrategy::priority)
            .selectProvider(context)
    }
}


