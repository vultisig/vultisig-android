package com.vultisig.wallet.ui.models.v3.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal sealed interface ChooseDeviceCountUiEvent {
    data object Back : ChooseDeviceCountUiEvent

    data class IndexChanged(val index: Int) : ChooseDeviceCountUiEvent

    data object Next : ChooseDeviceCountUiEvent
}

@HiltViewModel
internal class ChooseDeviceCountViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.ChooseVaultCount>()

    private val deviceCount = MutableStateFlow(1)

    fun handleEvent(event: ChooseDeviceCountUiEvent) {
        when (event) {
            ChooseDeviceCountUiEvent.Back -> back()
            is ChooseDeviceCountUiEvent.IndexChanged -> changeCount(event.index)
            ChooseDeviceCountUiEvent.Next -> next()
        }
    }

    private fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    private fun changeCount(index: Int) {
        // "Index" property of rive starts from 0
        deviceCount.update { (index + 1).coerceIn(1, 4) }
    }

    private fun next() {
        viewModelScope.launch {
            val count = deviceCount.value
            when (args.tssAction) {
                // Reshare reuses this picker but keeps the existing vault: skip the new-vault
                // name/email/password steps and hand the current vault straight to peer discovery.
                TssAction.ReShare -> {
                    val vaultId = args.vaultId ?: error("Reshare requires a vaultId")
                    val vault =
                        vaultRepository.get(vaultId) ?: error("Vault $vaultId does not exist")
                    navigator.route(
                        Route.Keygen.PeerDiscovery(
                            action = TssAction.ReShare,
                            vaultId = vaultId,
                            vaultName = vault.name,
                            deviceCount = count,
                        )
                    )
                }

                else ->
                    navigator.route(Route.SetupVaultInfo(count = count, tssAction = args.tssAction))
            }
        }
    }
}
