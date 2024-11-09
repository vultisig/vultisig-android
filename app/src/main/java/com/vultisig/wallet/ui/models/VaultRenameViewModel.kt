package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.IsVaultNameValid
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.UiText.StringResource
import com.vultisig.wallet.ui.utils.asString
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


@HiltViewModel
internal class VaultRenameViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val navigator: Navigator<Destination>,
    private val snackbarFlow: SnackbarFlow,
    private val isVaultNameValid: IsVaultNameValid,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.get<String>(ARG_VAULT_ID)!!

    private val vault = MutableStateFlow<Vault?>(null)
    val errorMessageState = MutableStateFlow<UiText?>(null)
    val isLoading = MutableStateFlow(false)

    val renameTextFieldState = TextFieldState()

    fun loadData() {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: error("No vault with $vaultId id")
            this@VaultRenameViewModel.vault.value = vault
            renameTextFieldState.setTextAndPlaceCursorAtEnd(vault.name)
        }
    }


    fun saveName() {
        viewModelScope.launch {
            if (errorMessageState.value != null)
                return@launch
            vault.value?.let { vault ->
                val newName = renameTextFieldState.text.toString()
                if (newName.isEmpty() || newName.length > TextFieldUtils.VAULT_NAME_MAX_LENGTH) {
                    snackbarFlow.showMessage(
                        StringResource(R.string.rename_vault_invalid_name).asString(context)
                    )
                    return@launch
                }
                showLoading()
                val isNameAlreadyExist =
                    vaultRepository.getAll().any { it.name == newName }
                if (isNameAlreadyExist) {
                    snackbarFlow.showMessage(
                        StringResource(R.string.vault_edit_this_name_already_exist).asString(context)
                    )
                    hideLoading()
                    return@launch
                }
                vaultRepository.setVaultName(vault.id, newName)
                navigator.navigate(
                    Destination.Home(),
                    NavigationOptions(clearBackStack = true)
                )
                hideLoading()
            }
        }
    }

    private fun showLoading(){
        isLoading.update {
            true
        }
    }

    private fun hideLoading(){
        isLoading.update {
            false
        }
    }

    fun validate() {
        viewModelScope.launch {
            val errorMessage = validateName(renameTextFieldState.text.toString())
            errorMessageState.value = errorMessage
        }
    }

    private suspend fun validateName(newName: String): UiText? {
        if (newName.isEmpty() || !isVaultNameValid(newName))
            return StringResource(R.string.rename_vault_invalid_name)
        val isNameAlreadyExist = vaultRepository.getAll().any { it.name == newName }
        if (isNameAlreadyExist) {
            return StringResource(R.string.vault_edit_this_name_already_exist)
        }
        return null
    }
}