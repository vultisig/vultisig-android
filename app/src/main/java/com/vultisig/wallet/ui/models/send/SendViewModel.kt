package com.vultisig.wallet.ui.models.send

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.AdvanceGasUiRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SendViewModel @Inject constructor(
    private val sendNavigator: Navigator<SendDst>,
    private val mainNavigator: Navigator<Destination>,
    val addressProvider: AddressProvider,
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val advanceGasUiRepository: AdvanceGasUiRepository,
) : ViewModel() {
    val dst = sendNavigator.destination
    val isKeysignFinished = MutableStateFlow(false)
    val currentVault: MutableState<Vault?> = mutableStateOf(null)

    private val vaultId = savedStateHandle.toRoute<Route.SignMessage>().vaultId

    init {
        viewModelScope.launch {
            vaultRepository.get(vaultId)
                ?.let {
                    currentVault.value = it
                }
        }
    }

    fun finishKeysign() {
        isKeysignFinished.value = true
    }

    fun onGasSettingsClick() {
        viewModelScope.launch {
            advanceGasUiRepository.showSettings()
        }
    }

    fun navigateToHome(useMainNavigator: Boolean) {
        viewModelScope.launch {
            if (isKeysignFinished.value) {
                mainNavigator.route(
                    Route.Home(),
                    NavigationOptions(
                        clearBackStack = true
                    )
                )
            }
            if (useMainNavigator) {
                mainNavigator.navigate(Destination.Back)
            } else {
                sendNavigator.navigate(SendDst.Back)
            }
        }
    }
}