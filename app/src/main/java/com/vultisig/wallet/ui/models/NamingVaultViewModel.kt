package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

internal data class NamingVaultUiModel(
    val name: String = "",
)

@HiltViewModel
internal class NamingVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    private val _uiModel = MutableStateFlow(NamingVaultUiModel())
    val uiModel = _uiModel.asStateFlow()
    val vaultSetupType =
        VaultSetupType.fromInt(
            (savedStateHandle.get<String>(Destination.NamingVault.ARG_VAULT_SETUP_TYPE)
                ?: "0").toInt()
        )

    fun onNameChanged(newName: String) {
        _uiModel.update { it.copy(name = newName) }
    }

}