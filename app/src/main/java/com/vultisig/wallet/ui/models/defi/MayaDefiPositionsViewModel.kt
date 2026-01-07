package com.vultisig.wallet.ui.models.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
internal class MayaDefiPositionsViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private lateinit var vaultId: String

    val state = MutableStateFlow(MayaDefiPositionsUiModel())

    fun setData(vaultId: VaultId) {
        this.vaultId = vaultId
        // TODO: Load balance visibility
        // TODO: Load saved positions
        // TODO: Load total value
        Timber.d("Maya DeFi Positions initialized for vault: $vaultId")
    }

    fun onTabSelected(tab: String) {
        viewModelScope.launch {
            state.value = state.value.copy(selectedTab = tab)
        }
    }

    fun setPositionSelectionDialogVisibility(show: Boolean) {
        viewModelScope.launch {
            if (show) {
                state.value = state.value.copy(
                    showPositionSelectionDialog = true,
                    tempSelectedPositions = state.value.selectedPositions
                )
            } else {
                state.value = state.value.copy(
                    showPositionSelectionDialog = false,
                    tempSelectedPositions = state.value.selectedPositions
                )
            }
        }
    }

    fun onPositionSelectionChange(positionTitle: String, isSelected: Boolean) {
        viewModelScope.launch {
            val updatedPositions = if (isSelected) {
                state.value.tempSelectedPositions + positionTitle
            } else {
                state.value.tempSelectedPositions - positionTitle
            }
            state.value = state.value.copy(tempSelectedPositions = updatedPositions)
        }
    }

    fun onPositionSelectionDone() {
        viewModelScope.launch {
            val selectedPositions = state.value.tempSelectedPositions

            // TODO: Save selected positions to repository

            state.value = state.value.copy(
                showPositionSelectionDialog = false,
                selectedPositions = selectedPositions
            )

            // TODO: Reload bonded nodes with new selection
            Timber.d("Updated selected positions: $selectedPositions")
        }
    }

    fun onClickBond(nodeAddress: String) {
        viewModelScope.launch {
            // TODO: Navigate to send screen for bonding to specific node
            Timber.d("Bond to node: $nodeAddress")
        }
    }

    fun onClickUnBond(nodeAddress: String) {
        viewModelScope.launch {
            // TODO: Navigate to send screen for unbonding from node
            Timber.d("Unbond from node: $nodeAddress")
        }
    }

    fun bondToNode() {
        viewModelScope.launch {
            // TODO: Navigate to send screen for bonding to any node
            Timber.d("Bond to node (generic)")
        }
    }

    fun onBackClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    companion object {
        internal const val DEFAULT_ZERO_BALANCE = "$0.00"
    }
}
