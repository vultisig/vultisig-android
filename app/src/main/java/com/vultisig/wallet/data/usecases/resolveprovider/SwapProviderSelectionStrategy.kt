package com.vultisig.wallet.data.usecases.resolveprovider

import com.vultisig.wallet.data.models.SwapProvider

internal interface SwapProviderSelectionStrategy {
    suspend fun selectProvider(context: SwapSelectionContext): SwapProvider?
    val priority: Int
        get() = 0
}