package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

internal data class SecretUiModel(val isSwapKitEnabled: Boolean = true)

@HiltViewModel
internal class SecretViewModel
@Inject
constructor(
    private val swapKitConfig: SwapKitConfig,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(SecretUiModel())

    init {
        viewModelScope.launch {
            swapKitConfig.isFeatureEnabled.collect { isSwapKitEnabled ->
                state.update { it.copy(isSwapKitEnabled = isSwapKitEnabled) }
            }
        }
    }

    /** Persists the SwapKit aggregator feature flag from the hidden Vault Settings toggle. */
    fun toggleSwapKit(state: Boolean) {
        viewModelScope.safeLaunch { swapKitConfig.setFeatureEnabled(state) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
