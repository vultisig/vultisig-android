package com.vultisig.wallet.ui.screens.vault_settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
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
) : ViewModel() {


    val uiModel = MutableStateFlow(VaultSettingsState())

    private val vaultId: String =
        savedStateHandle.get<String>(ARG_VAULT_ID)!!

    init {
        viewModelScope.launch {
            val hasFastSign = isVaultHasFastSignById(vaultId)
            uiModel.update {
                it.copy(
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
            navigator.navigate(Destination.BackupPassword(vaultId))
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

    fun signMessage() {
        viewModelScope.launch {
            navigator.navigate(Destination.SignMessage(vaultId))
        }
    }

}