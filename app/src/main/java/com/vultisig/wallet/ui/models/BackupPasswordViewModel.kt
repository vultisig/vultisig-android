package com.vultisig.wallet.ui.models

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.fileName
import com.vultisig.wallet.data.common.saveContentToUri
import com.vultisig.wallet.data.mappers.MapVaultToProto
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CreateVaultBackupUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

internal data class BackupPasswordState(
    val confirmPasswordErrorMessage: UiText? = null,
    val passwordErrorMessage: UiText? = null,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val disableEncryption: Boolean = false,
    val backupContent: String = "",
    val error: UiText? = null,
)

@HiltViewModel
internal class BackupPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val mapVaultToProto: MapVaultToProto,
    private val createVaultBackup: CreateVaultBackupUseCase,
    private val navigator: Navigator<Destination>,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val snackbarFlow: SnackbarFlow,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val passwordTextFieldState = TextFieldState()
    val confirmPasswordTextFieldState = TextFieldState()

    private val args = savedStateHandle.toRoute<Route.BackupPassword>()

    private val vaultId = args.vaultId
    private val vaultType = args.vaultType

    private val vault = MutableStateFlow<Vault?>(null)

    val uiState = MutableStateFlow(BackupPasswordState())

    private var hasWritePermission by mutableStateOf(
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    )

    private val permissionChannel = Channel<Boolean>()
    val permissionFlow = permissionChannel.receiveAsFlow()

    val saveFileChannel = Channel<String>()

    init {
        viewModelScope.launch {
            val vault = vaultRepository.get(vaultId)
                ?: error("Vault with id $vaultId not found")

            this@BackupPasswordViewModel.vault.value = vault
            if (!hasWritePermission) {
                permissionChannel.send(true)
            }
        }
    }

    private suspend fun generateVaultData(
        password: String?,
    ): VaultBackupData? {
        val vault = vault.firstOrNull() ?: return null
        val fileName = generateFileName(vault)
        val backup = createVaultBackup(
            mapVaultToProto(vault),
            password,
        )
        return if (backup != null) {
            VaultBackupData(
                fileName = fileName,
                data = backup,
            )
        } else null
    }

    fun dismissError() {
        uiState.update { it.copy(error = null) }
    }

    fun backupEncryptedVault() {
        if (validateConfirmPassword()) {
            val password = passwordTextFieldState.text.toString()
            backupVault(password)
        }
    }

    fun backupUnencryptedVault() {
        backupVault(null)
    }

    private fun backupVault(password: String?) {
        viewModelScope.launch {
            val backupData = generateVaultData(password) ?: return@launch
            uiState.value = uiState.value.copy(backupContent = backupData.data)
            saveFileChannel.send(backupData.fileName)
        }
    }

    private fun generateFileName(vault: Vault): String {
        val shareNamePart = when (vault.libType) {
            SigningLibType.GG20 -> "part"
            SigningLibType.DKLS -> "share"
        }

        val fileName =
            "${vault.name}-${vault.pubKeyECDSA.takeLast(4)}" +
                    "-$shareNamePart${vault.getVaultPart()}of${vault.signers.size}.vult"

        return fileName
    }

    fun validateConfirmPassword(): Boolean {
        val errorMessage =
            when {
                passwordTextFieldState.text.toString().isEmpty() -> {
                    UiText.StringResource(R.string.backup_password_screen_empty_password)
                }

                passwordTextFieldState.text.toString() != confirmPasswordTextFieldState.text.toString() -> {
                    UiText.StringResource(R.string.backup_password_screen_confirm_password_error_message)
                }

                else -> null
            }

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

    fun onPermissionResult(isGranted: Boolean) {
        if (!isGranted) {
            viewModelScope.launch {
                snackbarFlow.showMessage(
                    context.getString(R.string.backup_password_screen_permission_required)
                )
                navigator.navigate(
                    Destination.VaultSettings(vaultId),
                    NavigationOptions(clearBackStack = true)
                )
            }
        }
    }

    fun saveContentToUriResult(uri: Uri, content: String) {
        if (isFileExtensionValid(uri)) {
            val isSuccess = context.saveContentToUri(uri, content)
            completeBackupVault(isSuccess)
        } else {
            viewModelScope.launch {
                DocumentsContract.deleteDocument(context.contentResolver, uri)

                uiState.update {
                    it.copy(
                        error = UiText.FormattedText(
                            R.string.vault_settings_error_extension_backup_file,
                            listOf(FILE_ALLOWED_EXTENSIONS.joinToString(", "))
                        )
                    )
                }
            }
        }
    }

    private fun isFileExtensionValid(uri: Uri) =
        FILE_ALLOWED_EXTENSIONS.any {
            it == File(uri.fileName(context)).extension
        }

    private fun completeBackupVault(backupSuccess: Boolean) {
        viewModelScope.launch {
            if (backupSuccess) {
                vaultDataStoreRepository.setBackupStatus(vaultId, true)
                snackbarFlow.showMessage(
                    context.getString(
                        R.string.vault_settings_success_backup_message
                    )
                )
                if (vaultType != null) {
                    navigator.route(
                        Route.VaultConfirmation(
                            vaultId = vaultId,
                            vaultType = vaultType,
                        )
                    )
                } else {
                    navigator.navigate(
                        Destination.Home(vaultId),
                        NavigationOptions(clearBackStack = true)
                    )
                }
            } else {
                snackbarFlow.showMessage(
                    context.getString(R.string.vault_settings_error_backup_file)
                )
            }
        }
    }
}

private data class VaultBackupData(
    val fileName: String,
    val data: String,
)