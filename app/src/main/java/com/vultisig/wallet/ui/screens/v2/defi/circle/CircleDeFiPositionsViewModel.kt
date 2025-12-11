package com.vultisig.wallet.ui.screens.v2.defi.circle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class CircleDeFiPositionsViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val _state = MutableStateFlow(
        DefiUiModel(
            totalAmountPrice = "$0.00",
            isTotalAmountLoading = true,
            isBalanceVisible = true,
            supportEditChains = false,
            selectedTab = DeFiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner,
            containsTabDescription = true,
            containsTabWarningBanner = true,
        )
    )

    val state: StateFlow<DefiUiModel> = _state.asStateFlow()

    init {
        loadCirclePositions()
    }

    private fun loadCirclePositions() {
        viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    totalAmountPrice = "$5,432.10",
                    isTotalAmountLoading = false,
                    supportEditChains = true
                )
            }
        }
    }

    fun onTabSelected(tab: String) {
        _state.update { currentState ->
            currentState.copy(selectedTab = tab)
        }
        loadTabData(tab)
    }

    private fun loadTabData(tab: String) {
        viewModelScope.launch {
            _state.update { it.copy(isTotalAmountLoading = true) }

            when (tab) {
                DeFiTab.DEPOSITED.displayName -> {
                    _state.update { currentState ->
                        currentState.copy(
                            totalAmountPrice = "$5,432.10",
                            isTotalAmountLoading = false
                        )
                    }
                }

                else -> {
                    _state.update { currentState ->
                        currentState.copy(
                            totalAmountPrice = "$0.00",
                            isTotalAmountLoading = false
                        )
                    }
                }
            }
        }
    }

    fun onBackClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}