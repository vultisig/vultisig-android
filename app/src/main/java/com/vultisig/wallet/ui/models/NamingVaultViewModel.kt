package com.vultisig.wallet.ui.models

import androidx.lifecycle.ViewModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

}