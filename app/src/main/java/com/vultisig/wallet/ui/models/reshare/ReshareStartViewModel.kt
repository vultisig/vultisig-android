package com.vultisig.wallet.ui.models.reshare

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
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Which reshare entry the user tapped, used to gate the "Before you reshare" pre-flight sheet. */
internal enum class ReshareAction {
    Start,
    Join,
}

@HiltViewModel
internal class ReshareStartViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.ReshareStartScreen>().vaultId

    // Non-null when the "Before you reshare" sheet is shown; carries the action to run on confirm.
    private val pendingAction = MutableStateFlow<ReshareAction?>(null)
    val bottomSheetAction = pendingAction.asStateFlow()

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun start() {
        pendingAction.update { ReshareAction.Start }
    }

    fun join() {
        pendingAction.update { ReshareAction.Join }
    }

    fun dismissSheet() {
        pendingAction.update { null }
    }

    fun onConfirm() {
        val action = pendingAction.value ?: return
        pendingAction.update { null }
        viewModelScope.launch {
            when (action) {
                ReshareAction.Start -> {
                    val vault =
                        vaultRepository.get(vaultId)
                            ?: run {
                                navigator.navigate(Destination.Back)
                                return@launch
                            }
                    navigator.route(
                        Route.Keygen.PeerDiscovery(
                            action = TssAction.ReShare,
                            vaultId = vaultId,
                            vaultName = vault.name,
                        )
                    )
                }

                ReshareAction.Join -> navigator.route(Route.ScanQr())
            }
        }
    }
}
