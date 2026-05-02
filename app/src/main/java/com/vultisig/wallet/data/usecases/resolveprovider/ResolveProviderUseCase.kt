package com.vultisig.wallet.data.usecases.resolveprovider

import com.vultisig.wallet.data.models.SwapProvider
import javax.inject.Inject

internal interface ResolveProviderUseCase : suspend (SwapSelectionContext) -> SwapProvider?

internal class ResolveProviderUseCaseImpl
@Inject
constructor(
    private val defaultSwapProviderSelectionStrategy: DefaultSwapProviderSelectionStrategy
) : ResolveProviderUseCase {

    override suspend fun invoke(context: SwapSelectionContext): SwapProvider? =
        defaultSwapProviderSelectionStrategy.selectProvider(context)
}
