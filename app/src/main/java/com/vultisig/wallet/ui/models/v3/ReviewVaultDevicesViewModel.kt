package com.vultisig.wallet.ui.models.v3

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ReviewVaultDevicesUiState(
    val localPartyId: String = "",
    val devices: List<String> = emptyList(),
)


internal sealed interface ReviewVaultDevicesEvent {
    data object LooksGood : ReviewVaultDevicesEvent
    data object SomethingsWrong : ReviewVaultDevicesEvent
    data object Back : ReviewVaultDevicesEvent
}

@HiltViewModel
internal class ReviewVaultDevicesViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val args = savedStateHandle.toRoute<Route.ReviewVaultDevices>()

    val uiState = MutableStateFlow(
        ReviewVaultDevicesUiState(
            devices = args.devices.orEmpty(),
            localPartyId = args.localPartyId.orEmpty()
        )
    )

    fun onEvent(event: ReviewVaultDevicesEvent) {
        when (event) {
            ReviewVaultDevicesEvent.LooksGood -> looksGood()
            ReviewVaultDevicesEvent.SomethingsWrong -> back()
            ReviewVaultDevicesEvent.Back -> back()
        }
    }

    private fun looksGood() {
        viewModelScope.launch {
            navigator.route(
                route = Route.Onboarding.VaultBackup(
                    vaultId = args.vaultId,
                    pubKeyEcdsa = args.pubKeyEcdsa,
                    email = args.email,
                    vaultType = args.vaultType,
                    action = args.action,
                    vaultName = args.vaultName,
                    password = args.password,
                    deviceCount = args.devices?.size
                ),
            )
        }
    }

    private fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }
}
