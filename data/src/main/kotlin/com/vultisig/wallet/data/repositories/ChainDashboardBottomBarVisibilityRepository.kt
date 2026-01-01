package com.vultisig.wallet.data.repositories

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChainDashboardBottomBarVisibilityRepository @Inject constructor() {
    private val isBottomBarVisible = MutableStateFlow(true)

    fun hideBottomBar(){
        isBottomBarVisible.update { false }
    }

    fun showBottomBar(){
        isBottomBarVisible.update { true }
    }

    fun getBottomBarVisibility() = isBottomBarVisible
}