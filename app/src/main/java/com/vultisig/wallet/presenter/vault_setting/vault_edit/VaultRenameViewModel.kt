package com.vultisig.wallet.presenter.vault_setting.vault_edit

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.UiText.StringResource
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.presenter.common.TextFieldUtils
import com.vultisig.wallet.presenter.vault_setting.vault_edit.VaultEditUiEvent.ShowSnackBar
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.VaultSettings.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject


@OptIn(ExperimentalFoundationApi::class)
@HiltViewModel
internal class VaultRenameViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val navigator: Navigator<Destination>,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.get<String>(ARG_VAULT_ID)!!

    private val vault = MutableStateFlow<Vault?>(null)
    val errorMessageState = MutableStateFlow<UiText?>(null)

    val renameTextFieldState = TextFieldState()

    private val channel = Channel<VaultEditUiEvent>()
    val channelFlow = channel.receiveAsFlow()



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
                    channel.send(ShowSnackBar(StringResource(R.string.rename_vault_invalid_name)))
                    return@launch
                }
                val isNameAlreadyExist =
                    vaultRepository.getAll().any { it.name == newName }
                if (isNameAlreadyExist) {
                    channel.send(ShowSnackBar(StringResource(R.string.vault_edit_this_name_already_exist)))
                    return@launch
                }
                vaultRepository.setVaultName(vault.id, newName)
                navigator.navigate(Destination.Home())
            }
        }
    }

    fun validate() {
        viewModelScope.launch {
            val errorMessage = validateName(renameTextFieldState.text.toString())
            errorMessageState.value = errorMessage
        }
    }

    private suspend fun validateName(newName: String): UiText? {
        if (newName.isEmpty() || newName.length > TextFieldUtils.VAULT_NAME_MAX_LENGTH)
            return StringResource(R.string.rename_vault_invalid_name)
        val isNameAlreadyExist = vaultRepository.getAll().any { it.name == newName }
        if (isNameAlreadyExist) {
            return StringResource(R.string.vault_edit_this_name_already_exist)
        }
        return null
    }
}