package com.vultisig.wallet.ui.models.reshare

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ReshareStartViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val vaultId: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_ID])

    fun start() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.KeygenFlow(
                    vaultId = vaultId,
                    vaultName = null,
                    vaultSetupType = VaultSetupType.SECURE,
                    email = null,
                    password = null,
                )
            )
        }
    }

    fun startWithServer() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.KeygenEmail(
                    vaultId = vaultId,
                    name = null,
                    setupType = VaultSetupType.ACTIVE,
                )
            )
        }
    }

    fun join() {
        viewModelScope.launch {
            navigator.navigate(Destination.JoinThroughQr(null))
        }
    }

}