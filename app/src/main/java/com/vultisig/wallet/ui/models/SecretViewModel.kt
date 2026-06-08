package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.CustomRpcConfig
import com.vultisig.wallet.data.repositories.swap.SwapKitConfig
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SecretUiModel(
    val isSwapKitEnabled: Boolean = true,
    val isCustomRpcEnabled: Boolean = false,
)

@HiltViewModel
internal class SecretViewModel
@Inject
constructor(
    private val swapKitConfig: SwapKitConfig,
    private val customRpcConfig: CustomRpcConfig,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(SecretUiModel())

    init {
        viewModelScope.launch {
            swapKitConfig.isFeatureEnabled.collect { isSwapKitEnabled ->
                state.update { it.copy(isSwapKitEnabled = isSwapKitEnabled) }
            }
        }
        viewModelScope.launch {
            customRpcConfig.isFeatureEnabled.collect { isCustomRpcEnabled ->
                state.update { it.copy(isCustomRpcEnabled = isCustomRpcEnabled) }
            }
        }
    }

    /** Persists the SwapKit aggregator feature flag from the hidden Vault Settings toggle. */
    fun toggleSwapKit(state: Boolean) {
        viewModelScope.safeLaunch { swapKitConfig.setFeatureEnabled(state) }
    }

    /** Persists the Custom RPC feature flag (#4787) from the hidden Vault Settings toggle. */
    fun toggleCustomRpc(state: Boolean) {
        viewModelScope.safeLaunch { customRpcConfig.setFeatureEnabled(state) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
