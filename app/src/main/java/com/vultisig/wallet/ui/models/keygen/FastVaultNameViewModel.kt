package com.vultisig.wallet.ui.models.keygen

import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateRandomUniqueName
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class FastVaultNameState(
    val errorMessage: UiText? = null,
    val isNextButtonEnabled: Boolean = false,
)

@HiltViewModel
internal class FastVaultNameViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val uniqueName: GenerateRandomUniqueName,
    private val isNameLengthValid: IsVaultNameValid,
) : ViewModel() {

    val state = MutableStateFlow(FastVaultNameState())
    val textFieldState: TextFieldState = TextFieldState()
    private val vaultNamesList = MutableStateFlow<List<String>>(emptyList())

    init {
        viewModelScope.launch {
            vaultNamesList.update {
                vaultRepository.getAll().map { it.name }
            }
            textFieldState.textAsFlow().collectLatest {
                validate()
            }
        }
    }

    private fun validate() = viewModelScope.launch {
        val name = textFieldState.text.toString()
        val errorMessage = if (!isNameLengthValid(name))
            StringResource(R.string.naming_vault_screen_invalid_name)
        else null
        val isNextButtonEnabled = name.isNotEmpty() && errorMessage == null
        state.update {
            it.copy(
                errorMessage = errorMessage,
                isNextButtonEnabled = isNextButtonEnabled,
            )
        }
    }

    fun navigateToEmail() {
        if (state.value.errorMessage != null)
            return
        viewModelScope.launch {
            val name = Uri.encode(
                uniqueName(
                    textFieldState.text.toString(),
                    vaultNamesList.value
                )
            )
            navigator.route(
                Route.FastVaultInfo.Email(name)
            )
        }
    }

    fun clearInput() {
        textFieldState.clearText()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}