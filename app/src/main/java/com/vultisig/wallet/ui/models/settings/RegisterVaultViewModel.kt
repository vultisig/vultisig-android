package com.vultisig.wallet.ui.models.settings

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class RegisterVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    val vaultId = requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID))

    fun navigateToShareVaultQrScreen() {
        viewModelScope.launch {
            navigator.navigate(Destination.ShareVaultQr(vaultId))
        }
    }
}