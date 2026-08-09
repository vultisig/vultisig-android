package com.vultisig.wallet.ui.models.passcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.passcode.isConfigured
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

@Immutable
internal data class PasscodeSettingsUiModel(
    val isPasscodeEnabled: Boolean = false,
    val autoLockTimeout: AutoLockTimeout = AutoLockTimeout.Default,
    /**
     * False until the persisted credentials have been read; the switch must not act before then.
     */
    val isReady: Boolean = false,
)

/** Backs Settings → Passcode encryption: the on/off switch, change, and auto-lock entry points. */
@HiltViewModel
internal class PasscodeSettingsViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val passcodeRepository: PasscodeRepository,
    autoLockRepository: AutoLockRepository,
) : ViewModel() {

    val state = MutableStateFlow(PasscodeSettingsUiModel())

    init {
        viewModelScope.launch {
            passcodeRepository.initialize()
            combine(passcodeRepository.state, autoLockRepository.timeout) { passcode, timeout ->
                    PasscodeSettingsUiModel(
                        isPasscodeEnabled = passcode.isConfigured,
                        autoLockTimeout = timeout,
                        isReady = passcode != PasscodeState.Unknown,
                    )
                }
                .collect { state.value = it }
        }
    }

    /**
     * Both directions go through a prompt rather than acting on the switch alone: turning it on
     * needs a passcode to set, and turning it off needs proof the user knows the current one.
     */
    fun onPasscodeEnabledChange(enabled: Boolean) {
        // Until the persisted credentials have been read the switch reads as off even when a
        // passcode exists, and acting on that would route to Set and try to replace the wrap that
        // already-encrypted keyshares depend on.
        if (!state.value.isReady) return
        val action =
            if (enabled) Route.PasscodeEntryAction.Set else Route.PasscodeEntryAction.Disable
        viewModelScope.launch { navigator.route(Route.PasscodeEntry(action)) }
    }

    fun onChangePasscodeClick() {
        viewModelScope.launch {
            navigator.route(Route.PasscodeEntry(Route.PasscodeEntryAction.Change))
        }
    }

    fun onAutoLockClick() {
        viewModelScope.launch { navigator.route(Route.AutoLockSetting) }
    }
}
