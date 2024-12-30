package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class SecretUiModel(
    val isDklsEnabled: Boolean = false,
)

@HiltViewModel
internal class SecretViewModel @Inject constructor(
    private val secretSettingsRepository: SecretSettingsRepository,
) : ViewModel() {

    val state = MutableStateFlow(SecretUiModel())

    init {
        viewModelScope.launch {
            secretSettingsRepository.isDklsEnabled.collect { isDklsEnabled ->
                state.update { it.copy(isDklsEnabled = isDklsEnabled) }
            }
        }
    }

    fun toggleDkls(state: Boolean) {
        viewModelScope.launch {
            secretSettingsRepository.setDklsEnabled(state)
        }
    }

}