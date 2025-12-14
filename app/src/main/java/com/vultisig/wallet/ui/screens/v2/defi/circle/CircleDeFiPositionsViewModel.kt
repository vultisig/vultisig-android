package com.vultisig.wallet.ui.screens.v2.defi.circle

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.ScaCircleAccountRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.DeFiTab
import com.vultisig.wallet.ui.screens.v2.defi.model.DefiUiModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
internal class CircleDeFiPositionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val scaCircleAccountRepository: ScaCircleAccountRepository,
) : ViewModel() {

    private var vaultId: String = savedStateHandle.toRoute<Route.PositionCircle>().vaultId

    private val _state = MutableStateFlow(
        DefiUiModel(
            totalAmountPrice = "$0.00",
            isTotalAmountLoading = true,
            isBalanceVisible = true,
            supportEditChains = false,
            selectedTab = DeFiTab.DEPOSITED.displayName,
            bannerImage = R.drawable.circle_defi_banner,
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
                    isTotalAmountLoading = true,
                    circleDefi = currentState.circleDefi.copy(
                        isLoading = true
                    )
                )
            }

            val hideWarning = withContext(Dispatchers.IO){
                scaCircleAccountRepository.getCloseWarning()
            }

            _state.update { currentState ->
                currentState.copy(
                    circleDefi = currentState.circleDefi.copy(
                        closeWarning = hideWarning
                    )
                )
            }

            val addressSca = withContext(Dispatchers.IO){
                scaCircleAccountRepository.getAccount(vaultId)
            }

            _state.update { currentState ->
                currentState.copy(
                    totalAmountPrice = "$5,432.10",
                    isTotalAmountLoading = false,
                    supportEditChains = true,
                    circleDefi = currentState.circleDefi.copy(
                        isLoading = false
                    )
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

    fun onClickCloseWarning() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                scaCircleAccountRepository.saveCloseWarning()
            }
            _state.update { currentState ->
                currentState.copy(
                    circleDefi = currentState.circleDefi.copy(
                        closeWarning = true,
                    )
                )
            }
        }
    }
}