package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal sealed class KeyImportSetupType {
    data object Fast : KeyImportSetupType()
    data object Secure : KeyImportSetupType()
}

internal data class KeyImportDeviceCountUiModel(
    val selectedType: KeyImportSetupType = KeyImportSetupType.Fast,
)

@HiltViewModel
internal class KeyImportDeviceCountViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(KeyImportDeviceCountUiModel())

    fun selectType(type: KeyImportSetupType) {
        state.update { it.copy(selectedType = type) }
    }

    fun getStarted() {
        viewModelScope.launch {
            val vaultType = when (state.value.selectedType) {
                KeyImportSetupType.Fast -> Route.VaultInfo.VaultType.Fast
                KeyImportSetupType.Secure -> Route.VaultInfo.VaultType.Secure
            }
            navigator.route(
                Route.VaultInfo.Name(
                    vaultType = vaultType,
                    tssAction = TssAction.KeyImport,
                )
            )
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}
