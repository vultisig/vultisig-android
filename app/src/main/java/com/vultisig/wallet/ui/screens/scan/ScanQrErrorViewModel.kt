package com.vultisig.wallet.ui.screens.scan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ScanQrErrorViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID))

    fun enableChain() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.AddChainAccount(
                    vaultId = vaultId
                ),
                opts = NavigationOptions(popUpTo = Destination.Home().route)
            )
        }
    }
}