package com.vultisig.wallet.ui.models.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class VaultBackupSummaryUiModel(
    val isConsentChecked: Boolean = false,
    val vaultType: Route.VaultInfo.VaultType,
    val vaultShares: Int = 0,
)

@HiltViewModel
internal class VaultBackupSummaryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VaultBackupSummary>()

    val state = MutableStateFlow(
        VaultBackupSummaryUiModel(
            vaultType = args.vaultType
        )
    )

    init {
        viewModelScope.launch {
            vaultRepository.get(vaultId = args.vaultId)?.let { vault ->
                state.update { it.copy(vaultShares = vault.signers.size) }
            }
        }
    }

    fun toggleCheck(isChecked: Boolean) {
        state.update { it.copy(isConsentChecked = isChecked) }
    }

    fun next() {
        if (state.value.isConsentChecked) {
            viewModelScope.launch {
                navigator.route(
                    route = Route.Home(),
                    opts = NavigationOptions(
                        clearBackStack = true,
                    ),
                )
            }
        }
    }

    fun chooseChains() {
//        if (state.value.isConsentChecked) {
//            viewModelScope.launch {
//                navigator.route(
//                    route = Route.AddChainAccount(
//                        vaultId = args.vaultId,
//                    ),
//                    opts = NavigationOptions(
//                        clearBackStack = true,
//                    ),
//                )
//            }
//        }
    }

}