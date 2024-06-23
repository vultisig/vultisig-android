package com.vultisig.wallet.ui.models

import android.content.Context
import android.os.Build
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.vultisig.wallet.R
import com.vultisig.wallet.common.CryptoManager
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.common.backupVaultToDownloadsDir
import com.vultisig.wallet.common.encodeToHex
import com.vultisig.wallet.data.mappers.VaultAndroidToIOSMapper
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import javax.inject.Inject

data class BackupPasswordState(
    val confirmPasswordErrorMessage: UiText? = null,
    val passwordErrorMessage: UiText? = null,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val disableEncryption: Boolean = false,
)

@HiltViewModel
@OptIn(ExperimentalFoundationApi::class)
internal class BackupPasswordViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val vaultAndroidToIOSMapper: VaultAndroidToIOSMapper,
    savedStateHandle: SavedStateHandle,
    private val gson: Gson,
    private val navigator: Navigator<Destination>,
    private val cryptoManager: CryptoManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val snackbarHostState = SnackbarHostState()
    val passwordTextFieldState = TextFieldState()
    val confirmPasswordTextFieldState = TextFieldState()

    private val vaultId: String =
        savedStateHandle.get<String>(Destination.BackupPassword.ARG_VAULT_ID)!!

    private val vault = MutableStateFlow<Vault?>(null)

    val uiState = MutableStateFlow(BackupPasswordState())


    init {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId) ?: return@launch
            this@BackupPasswordViewModel.vault.value = vault
        }
    }

    private fun backupFile(json: String, backupFileName: String, encryptDataEnabled: Boolean) {
        val dataToBackup: String
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            dataToBackup = if (encryptDataEnabled && passwordIsValid())
                encryptData(json, passwordTextFieldState.text.toString())
            else json

            val isSuccess = context.backupVaultToDownloadsDir(dataToBackup, backupFileName)
            viewModelScope.launch {
                if (isSuccess) {
                    navigator.navigate(
                        Destination.Home(vaultId), NavigationOptions(
                            clearBackStack = true
                        )
                    )
                } else {
                    snackbarHostState.showSnackbar(
                        context.getString(
                            R.string.vault_settings_error_backup_file,
                        )
                    )
                }
            }
        }
    }

    private fun encryptData(date: String, key: String): String {
        return cryptoManager.encrypt(date, key)
    }

    private fun passwordIsValid(): Boolean =
        passwordTextFieldState.text.toString().isNotEmpty() &&
                passwordTextFieldState.text.toString() == confirmPasswordTextFieldState.text.toString()


    fun backupVault(encryptDataEnabled: Boolean) {
        if (encryptDataEnabled && (!validatePassword() || !validateConfirmPassword()))
            return
        viewModelScope.launch {
            val vault = vault.firstOrNull() ?: return@launch
            val thresholds = Utils.getThreshold(vault.signers.count())
            val date = Date()
            val format = SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
            val formattedDate = format.format(date)
            val fileName =
                "vultisig-${vault.name}-$formattedDate-${thresholds + 1}of${vault.signers.count()}-${
                    vault.pubKeyECDSA.takeLast(4)
                }-${vault.localPartyID}.dat"

            val vaultJson = gson.toJson(vaultAndroidToIOSMapper(vault)).encodeToHex()
            backupFile(vaultJson, fileName, encryptDataEnabled)
        }
    }

    fun validatePassword(): Boolean {
        val errorMessage = if (passwordTextFieldState.text.toString().isEmpty())
            UiText.StringResource(R.string.backup_password_screen_password_is_empty) else null
        uiState.update {
            it.copy(passwordErrorMessage = errorMessage)
        }
        return errorMessage == null
    }

    fun validateConfirmPassword(): Boolean {
        val errorMessage =
            if (passwordTextFieldState.text.toString() != confirmPasswordTextFieldState.text.toString())
                UiText.StringResource(R.string.backup_password_screen_confirm_password_error_message)
            else null

        uiState.update {
            it.copy(confirmPasswordErrorMessage = errorMessage)
        }
        return errorMessage == null
    }

    fun togglePasswordVisibility() {
        val isPasswordVisible = !uiState.value.isPasswordVisible
        uiState.update {
            it.copy(isPasswordVisible = isPasswordVisible)
        }
    }

    fun toggleConfirmPasswordVisibility() {
        val isConfirmPasswordVisible = !uiState.value.isConfirmPasswordVisible
        uiState.update {
            it.copy(isConfirmPasswordVisible = isConfirmPasswordVisible)
        }
    }

}