package com.vultisig.wallet.ui.models.passcode

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.passcode.AutoLockRepository
import com.vultisig.wallet.data.passcode.AutoLockTimeout
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Immutable
internal data class AutoLockSettingUiModel(
    val options: List<AutoLockTimeout> = AutoLockTimeout.entries,
    val selected: AutoLockTimeout = AutoLockTimeout.Default,
)

/** Backs the auto-lock timeout picker. */
@HiltViewModel
internal class AutoLockSettingViewModel
@Inject
constructor(private val autoLockRepository: AutoLockRepository) : ViewModel() {

    val state = MutableStateFlow(AutoLockSettingUiModel())

    init {
        viewModelScope.launch {
            autoLockRepository.timeout.collect { timeout ->
                state.value = state.value.copy(selected = timeout)
            }
        }
    }

    fun onTimeoutClick(timeout: AutoLockTimeout) {
        viewModelScope.launch { autoLockRepository.setTimeout(timeout) }
    }
}
