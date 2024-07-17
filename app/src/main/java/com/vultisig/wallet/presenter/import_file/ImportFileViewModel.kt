package com.vultisig.wallet.presenter.import_file

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.fileContent
import com.vultisig.wallet.common.fileName
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCase
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@OptIn(ExperimentalFoundationApi::class)
@HiltViewModel
internal class ImportFileViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val saveVault: SaveVaultUseCase,
    private val parseVaultFromString: ParseVaultFromStringUseCase,
) : ViewModel() {
    val uiModel = MutableStateFlow(ImportFileState())

    val passwordTextFieldState = TextFieldState()

    private val snackBarChannel = Channel<UiText?>()
    val snackBarChannelFlow = snackBarChannel.receiveAsFlow()


    fun hidePasswordPromptDialog() {
        uiModel.update {
            it.copy(showPasswordPrompt = false)
        }
    }

    fun decryptVaultData() {
        val key: String = passwordTextFieldState.text.toString()
        val vaultFileContent = uiModel.value.fileContent
        if (vaultFileContent != null) {
            viewModelScope.launch {
                try {
                    saveToDb(vaultFileContent, key)
                    hidePasswordPromptDialog()
                } catch (e: Exception) {
                    Timber.e(e)
                    showErrorHint()
                }
            }
        }
    }


    private fun parseFileContent(fileContent: String?) {
        if (fileContent == null)
            return
        viewModelScope.launch {
            try {
                saveToDb(fileContent, null)
            } catch (e: Exception) {
                uiModel.update {
                    it.copy(
                        showPasswordPrompt = true,
                        passwordErrorHint = null
                    )
                }
            }

        }
    }

    private suspend fun saveToDb(fileContent: String, password: String?) {
        try {
            insertVaultToDb(parseVaultFromString(fileContent, password))
        } catch (e: SQLiteConstraintException) {
            snackBarChannel.send(UiText.StringResource(R.string.import_file_screen_duplicate_vault))
        }
    }

    private suspend fun insertVaultToDb(vault: Vault) {
        saveVault(vault, false)
        vaultDataStoreRepository.setBackupStatus(vault.id, true)
        navigator.navigate(
            Destination.Home(
                openVaultId = vault.id,
            )
        )
    }


    fun removeSelectedFile() {
        uiModel.update {
            it.copy(fileUri = null, fileName = null, fileContent = null)
        }
    }

    fun saveFileToAppDir() {
        val uri = uiModel.value.fileUri ?: return
        val fileContent = uri.fileContent(context)
        uiModel.update {
            it.copy(fileContent = fileContent)
        }
        parseFileContent(fileContent)
    }

    fun fetchFileName(uri: Uri?) {
        uiModel.update {
            it.copy(fileUri = uri, fileName = null, fileContent = null)
        }
        if (uri == null)
            return
        val fileName = uri.fileName(context)
        uiModel.update {
            it.copy(fileName = fileName)
        }
    }

    private fun showErrorHint() {
        uiModel.update {
            it.copy(passwordErrorHint = UiText.StringResource(R.string.import_file_screen_password_error))
        }
    }

    fun togglePasswordVisibility() {
        val passwordVisibility = uiModel.value.isPasswordObfuscated
        uiModel.update {
            it.copy(isPasswordObfuscated = !passwordVisibility)
        }
    }

}