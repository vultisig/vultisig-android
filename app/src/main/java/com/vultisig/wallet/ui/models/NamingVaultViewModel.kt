package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.KeygenFlow.Companion.DEFAULT_NEW_VAULT
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class NamingVaultUiModel(
    val name: String = ""
)

@HiltViewModel
internal class NamingVaultViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    private val _uiModel = MutableStateFlow(NamingVaultUiModel())
    val uiModel = _uiModel.asStateFlow()

    fun onNameChanged(newName: String) {
        _uiModel.update { it.copy(name = newName) }
    }

    fun onContinueClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.KeygenFlow(
                uiModel.value.name.takeIf { it.isNotEmpty() } ?: DEFAULT_NEW_VAULT))
        }
    }
}