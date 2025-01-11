package com.vultisig.wallet.data.repositories

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject

interface RefreshQuoteUiRepository {
    val refreshValue: MutableStateFlow<Boolean>

    fun triggerRefreshQuote()
}

internal class RefreshQuoteUiRepositoryImpl @Inject constructor() : RefreshQuoteUiRepository {
    override val refreshValue = MutableStateFlow(false)

    override fun triggerRefreshQuote() {
        refreshValue.value = !refreshValue.value
    }
}