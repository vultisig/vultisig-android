package com.vultisig.wallet.ui.screens.vault_settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal open class VaultSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val vaultRepository: VaultRepository,
) : ViewModel() {


    val uiModel = MutableStateFlow(VaultSettingsState())

    private val vaultId: String =
        savedStateHandle.get<String>(ARG_VAULT_ID)!!

    init {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
            val hasMigration = vault?.libType == SigningLibType.GG20
            val hasFastSign = isVaultHasFastSignById(vaultId) && vault?.signers?.count() == 2
            uiModel.update {
                it.copy(
                    hasReshare = !hasFastSign,
                    hasMigration = hasMigration,
                    hasFastSign = hasFastSign
                )
            }
        }
    }

    fun openDetails() {
        viewModelScope.launch {
            navigator.navigate(Destination.Details(vaultId))
        }
    }

    fun openRename() {
        viewModelScope.launch {
            navigator.navigate(Destination.Rename(vaultId))
        }
    }

    fun navigateToBackupPasswordScreen() {
        viewModelScope.launch {
            navigator.route(Route.BackupPasswordRequest(vaultId))
        }
    }

    fun navigateToConfirmDeleteScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.ConfirmDelete(vaultId))
        }
    }

    fun navigateToReshareStartScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.ReshareStartScreen(vaultId))
        }
    }

    fun navigateToBiometricsScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.BiometricsEnable(vaultId))
        }
    }

    fun navigateToOnChainSecurityScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.OnChainSecurity)
        }
    }

    fun signMessage() {
        viewModelScope.launch {
            navigator.navigate(Destination.SignMessage(vaultId))
        }
    }

    fun migrate() {
        viewModelScope.launch {
            navigator.route(Route.Migration.Onboarding(vaultId = vaultId))
        }
    }
}