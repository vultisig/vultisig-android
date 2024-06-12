package com.vultisig.wallet.ui.models

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.textAsFlow
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.presenter.common.TextFieldUtils
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
@OptIn(ExperimentalFoundationApi::class)
internal class NamingVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
) : ViewModel() {


    val namingTextFieldState = TextFieldState()
    val errorMessageState = MutableStateFlow<UiText?>(null)

    init {
        collectNamingFieldStateChanges()
    }

    private fun collectNamingFieldStateChanges() {
        viewModelScope.launch {
            namingTextFieldState.textAsFlow().collect { newName ->
                val errorMessage = validateVaultName(newName.toString())
                errorMessageState.update { errorMessage }
            }
        }
    }

    private val vaultSetupType =
        VaultSetupType.fromInt(
            (savedStateHandle.get<String>(Destination.NamingVault.ARG_VAULT_SETUP_TYPE)
                ?: "0").toInt()
        )

    private fun validateVaultName(s: String): UiText? {
        if (isNameNotValid(s))
            return UiText.StringResource(R.string.naming_vault_screen_invalid_name)
        return null
    }

    fun navigateToKeygen() {
        val name = namingTextFieldState.text.toString()
        if (isNameNotValid(name))
            return
        viewModelScope.launch {
            navigator.navigate(
                Destination.KeygenFlow(
                    name.takeIf { it.isNotEmpty() }
                        ?: Destination.KeygenFlow.DEFAULT_NEW_VAULT,
                    vaultSetupType)
            )
        }
    }

    private fun isNameNotValid(s: String) =
        s.isEmpty() || s.length > TextFieldUtils.VAULT_NAME_MAX_LENGTH
}