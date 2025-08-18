package com.vultisig.wallet.ui.screens.migration

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class MigrationOnboardingUiModel(
    val vaultType: Route.VaultInfo.VaultType = Route.VaultInfo.VaultType.Secure,
)

@HiltViewModel
internal class MigrationOnboardingViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
) : ViewModel() {

    val state = MutableStateFlow(MigrationOnboardingUiModel())

    private val args = savedStateHandle.toRoute<Route.Migration.Onboarding>()
    private val vaultId = args.vaultId

    init {
        viewModelScope.launch {
            vaultRepository.get(vaultId = vaultId)?.let { vault ->
                state.update {
                    it.copy(
                        vaultType = if (vault.isFastVault()  && vault.signers.size != 2 && vault.libType== SigningLibType.GG20) {
                            Route.VaultInfo.VaultType.Fast
                        } else {
                            Route.VaultInfo.VaultType.Secure
                        }
                    )
                }
            }
        }
    }

    fun upgrade() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: error("Vault with $vaultId doesn't exist")

            if (vault.isFastVault() && vault.signers.size != 2 && vault.libType== SigningLibType.GG20) {
                navigator.route(
                    Route.Migration.Password(
                        vaultId = vaultId
                    )
                )
            } else {
                navigator.route(
                    Route.Keygen.PeerDiscovery(
                        action = TssAction.Migrate,
                        vaultName = vault.name,
                        vaultId = vaultId,
                    )
                )
            }
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

}