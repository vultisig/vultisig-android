package com.vultisig.wallet.ui.screens.vault_settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
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
) : ViewModel() {


    val uiModel = MutableStateFlow(VaultSettingsState())

    private val vaultId: String =
        savedStateHandle.get<String>(Destination.VaultSettings.ARG_VAULT_ID)!!

    init {
        viewModelScope.launch {
            uiModel.update {
                it.copy(id = vaultId)
            }
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


}