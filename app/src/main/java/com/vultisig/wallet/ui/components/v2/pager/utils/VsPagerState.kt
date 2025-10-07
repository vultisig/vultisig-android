package com.vultisig.wallet.ui.components.v2.pager.utils

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember

@Stable
internal class VsPagerState {
    private val _pages = mutableStateListOf<@Composable () -> Unit>()

    val pages: List<@Composable () -> Unit>
        get() = _pages

    val pageCount: Int
        get() = _pages.size

    val currentPage: Int
        get() = _currentPage.intValue
    private val _currentPage = mutableIntStateOf(0)

    fun item(content: @Composable () -> Unit) {
        _pages.add(content)
    }

    fun updateCurrentPage(page: Int) {
        _currentPage.intValue = page
    }

    fun clear() {
        _pages.clear()
    }

}

@Composable
internal fun rememberVsPagerState(key: Any?) = remember(key1 = key) {
    VsPagerState()
}
