package com.vultisig.wallet.ui.models

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.saveContentToUri
import com.vultisig.wallet.data.mappers.MapVaultToProto
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CreateVaultBackupUseCase
import com.vultisig.wallet.data.usecases.backup.CreateVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.FILE_ALLOWED_EXTENSIONS
import com.vultisig.wallet.data.usecases.backup.IsVaultBackupFileExtensionValidUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.backup.PasswordState
import com.vultisig.wallet.ui.screens.backup.PasswordViewModelDelegate
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class BackupPasswordState(
    var isMoreInfoVisible: Boolean = false,
    val passwordErrorMessage: UiText? = null,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isNextButtonEnabled: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
internal class BackupPasswordViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    private val mapVaultToProto: MapVaultToProto,
    private val createVaultBackupFileName: CreateVaultBackupFileNameUseCase,
    private val createVaultBackup: CreateVaultBackupUseCase,
    private val isFileExtensionValid: IsVaultBackupFileExtensionValidUseCase,
    private val navigator: Navigator<Destination>,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val snackbarFlow: SnackbarFlow
) : ViewModel() {

    private val passwordDelegate = PasswordViewModelDelegate()

    val passwordTextFieldState = passwordDelegate.passwordTextFieldState
    val confirmPasswordTextFieldState = passwordDelegate.confirmPasswordTextFieldState

    private val args = savedStateHandle.toRoute<Route.BackupPassword>()

    private val vaultId = args.vaultId
    private val vaultType = args.vaultType

    private val vault = MutableStateFlow<Vault?>(null)

    val state = MutableStateFlow(BackupPasswordState())

    val createDocumentRequestFlow = MutableSharedFlow<String>()

    private var isMoreInfoVisible: Boolean
        get() = state.value.isMoreInfoVisible
        set(value) = state.update {
            it.copy(isMoreInfoVisible = value)
        }

    init {
        viewModelScope.launch {
            vault.value = vaultRepository.get(vaultId)
                ?: error("Vault with id $vaultId not found")
        }

        viewModelScope.launch {
            passwordDelegate.validatePasswords()
                .collect { passwordState ->
                    val errorMessage = if (passwordState is PasswordState.Mismatch) {
                        UiText.StringResource(R.string.fast_vault_password_screen_error)
                    } else {
                        null
                    }

                    state.update { state ->
                        state.copy(
                            passwordErrorMessage = errorMessage,
                            isNextButtonEnabled = passwordState is PasswordState.Valid,
                        )
                    }
                }
        }
    }

    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.Back,
            )
        }
    }

    fun backupEncryptedVault() {
        viewModelScope.launch {
            val vault = vault.firstOrNull() ?: error("No vault found")
            val fileName = createVaultBackupFileName(vault)
            createDocumentRequestFlow.emit(fileName)
        }
    }
    fun showMoreInfo() {
        isMoreInfoVisible = true
    }

    fun hideMoreInfo() {
        isMoreInfoVisible = false
    }

    fun togglePasswordVisibility() {
        state.update {
            it.copy(isPasswordVisible = !it.isPasswordVisible)
        }
    }

    fun toggleConfirmPasswordVisibility() {
        state.update {
            it.copy(isConfirmPasswordVisible = !it.isConfirmPasswordVisible)
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

    fun saveContentToUriResult(uri: Uri) {
        val password = passwordTextFieldState.text.toString()

        if (isFileExtensionValid(uri)) {
            val vault = vault.value ?: error("No vault on saveContent")

            val backup = createVaultBackup(
                mapVaultToProto(vault),
                password,
            ) ?: error("Failed to create vault backup data returns null")

            val isSuccess = context.saveContentToUri(uri, backup)
            completeBackupVault(isSuccess)
        } else {
            viewModelScope.launch {
                DocumentsContract.deleteDocument(context.contentResolver, uri)

                state.update {
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
                            action = args.action,
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
