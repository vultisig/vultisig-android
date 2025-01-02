package com.vultisig.wallet.ui.models.swap

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.RefreshQuoteUiRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class SwapViewModel @Inject constructor(
    private val sendNavigator: Navigator<SendDst>,
    private val mainNavigator: Navigator<Destination>,
    val addressProvider: AddressProvider,
    private val vaultRepository: VaultRepository,
    private val refreshQuoteUiRepository: RefreshQuoteUiRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val dst = sendNavigator.destination
    val currentVault: MutableState<Vault?> = mutableStateOf(null)
    val isKeysignFinished = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            vaultRepository.get(requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID)))?.let {
                currentVault.value = it
            }
        }
    }

    fun finishKeysign() {
        isKeysignFinished.value = true
    }

    fun requestToRefreshQuote(){
        refreshQuoteUiRepository.triggerRefreshQuote()
    }

    fun navigateToHome(useMainNavigator: Boolean) {
        viewModelScope.launch {
            if (isKeysignFinished.value) {
                mainNavigator.navigate(
                    Destination.Home(),
                    NavigationOptions(
                        clearBackStack = true
                    )
                )
            }
            if (useMainNavigator) {
                mainNavigator.navigate(Destination.Back)
            } else {
                sendNavigator.navigate(SendDst.Back)
            }
        }
    }
}