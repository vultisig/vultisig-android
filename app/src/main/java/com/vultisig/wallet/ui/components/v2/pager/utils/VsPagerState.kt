package com.vultisig.wallet.ui.components.v2.pager.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

@Stable
internal class VsPagerState internal constructor() {
    private val _pages = mutableStateListOf<@Composable () -> Unit>()

    val pages: List<@Composable () -> Unit>
        get() = _pages

    val pageCount: Int
        get() = _pages.size

    val currentPageValue = mutableIntStateOf(0)

    val currentPage: Int
        get() = currentPageValue.intValue

    fun item(content: @Composable () -> Unit) {
        _pages.add(content)
    }

    fun updateCurrentPage(page: Int) {
        currentPageValue.intValue = page
    }

    internal fun clear() {
        _pages.clear()
    }

}

@Composable
internal fun rememberVsPagerState(key1: Any?): VsPagerState {
    return remember(key1 = key1) {
        VsPagerState()
    }
}
