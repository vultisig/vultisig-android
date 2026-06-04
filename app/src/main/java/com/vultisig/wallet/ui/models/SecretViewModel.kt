package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.repositories.swap.SwapKitConfig
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal data class SecretUiModel(
    val isDklsEnabled: Boolean = false,
    val isSwapKitEnabled: Boolean = true,
)

@HiltViewModel
internal class SecretViewModel
@Inject
constructor(
    private val secretSettingsRepository: SecretSettingsRepository,
    private val swapKitConfig: SwapKitConfig,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val state = MutableStateFlow(SecretUiModel())

    init {
        viewModelScope.launch {
            secretSettingsRepository.isDklsEnabled.collect { isDklsEnabled ->
                state.update { it.copy(isDklsEnabled = isDklsEnabled) }
            }
        }

        viewModelScope.launch {
            swapKitConfig.isFeatureEnabled.collect { isSwapKitEnabled ->
                state.update { it.copy(isSwapKitEnabled = isSwapKitEnabled) }
            }
        }
    }

    fun toggleDkls(state: Boolean) {
        viewModelScope.launch { secretSettingsRepository.setDklsEnabled(state) }
    }

    /** Persists the SwapKit aggregator feature flag from the hidden Vault Settings toggle. */
    fun toggleSwapKit(state: Boolean) {
        viewModelScope.launch { swapKitConfig.setFeatureEnabled(state) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
