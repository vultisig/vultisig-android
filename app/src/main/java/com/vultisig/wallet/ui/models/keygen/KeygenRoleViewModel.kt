package com.vultisig.wallet.ui.models.keygen

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class KeygenRoleViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val stateHandle: SavedStateHandle,
) : ViewModel() {

    val vaultId = stateHandle.remove<String>(ARG_VAULT_ID)

    fun initiate() {
        viewModelScope.launch {
            val nextPage = if (vaultId == null)
                Destination.SelectVaultType()
            else
                Destination.KeygenFlow(
                    vaultId,
                    VaultSetupType.M_OF_N,
                    true,
                    email = null,
                    password = null,
                )
            navigator.navigate(nextPage)
        }
    }

    fun pair() {
        viewModelScope.launch {
            navigator.navigate(Destination.JoinThroughQr(null))
        }
    }

    fun import() {
        viewModelScope.launch {
            navigator.navigate(Destination.ImportVault)
        }
    }

}