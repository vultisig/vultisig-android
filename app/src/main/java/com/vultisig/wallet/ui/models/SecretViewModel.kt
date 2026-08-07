package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.passcode.PasscodeConfig
import com.vultisig.wallet.data.repositories.CustomRpcConfig
import com.vultisig.wallet.data.repositories.swap.LimitSwapConfig
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
    val isLimitSwapEnabled: Boolean = false,
    val isPasscodeEnabled: Boolean = false,
)

@HiltViewModel
internal class SecretViewModel
@Inject
constructor(
    private val swapKitConfig: SwapKitConfig,
    private val customRpcConfig: CustomRpcConfig,
    private val limitSwapConfig: LimitSwapConfig,
    private val passcodeConfig: PasscodeConfig,
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
        viewModelScope.launch {
            limitSwapConfig.isFeatureEnabled.collect { isLimitSwapEnabled ->
                state.update { it.copy(isLimitSwapEnabled = isLimitSwapEnabled) }
            }
        }
        viewModelScope.launch {
            passcodeConfig.isFeatureEnabled.collect { isPasscodeEnabled ->
                state.update { it.copy(isPasscodeEnabled = isPasscodeEnabled) }
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

    /** Persists the Limit Swap feature flag from the hidden Vault Settings toggle. */
    fun toggleLimitSwap(state: Boolean) {
        viewModelScope.safeLaunch { limitSwapConfig.setFeatureEnabled(state) }
    }

    /**
     * Persists the passcode app-lock feature flag from the hidden Vault Settings toggle. Turning it
     * off only hides the entry point for users who have not set a passcode: see [PasscodeConfig].
     */
    fun togglePasscode(state: Boolean) {
        viewModelScope.safeLaunch { passcodeConfig.setFeatureEnabled(state) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
