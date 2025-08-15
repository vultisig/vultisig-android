package com.vultisig.wallet.ui.models.reshare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ReshareStartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val vaultId: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_ID])

    fun start() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: error("Vault $vaultId does not exist")

            navigator.route(
                Route.Keygen.PeerDiscovery(
                    action = TssAction.ReShare,
                    vaultId = vaultId,
                    vaultName = vault.name,
                )
            )
        }
    }

    fun join() {
        viewModelScope.launch {
            navigator.route(Route.ScanQr())
        }
    }
}