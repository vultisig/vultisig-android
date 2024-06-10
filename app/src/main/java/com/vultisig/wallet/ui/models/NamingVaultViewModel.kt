package com.vultisig.wallet.ui.models

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.ui.models.keygen.VaultSetupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
@OptIn(ExperimentalFoundationApi::class)
internal class NamingVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>
) : ViewModel() {


    val namingTextFiledState = TextFieldState()

    private val vaultSetupType =
        VaultSetupType.fromInt(
            (savedStateHandle.get<String>(Destination.NamingVault.ARG_VAULT_SETUP_TYPE)
                ?: "0").toInt()
        )

    fun validateVaultName(s: String): UiText? {
        if (isNameNotValid(s))
            return UiText.StringResource(R.string.naming_vault_screen_invalid_name)
        return null
    }

    fun navigateToKeygen() {
        val name = namingTextFiledState.text.toString()
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

    private fun isNameNotValid(s: String) = s.isEmpty() || s.length > 50
}