package com.vultisig.wallet.ui.screens.backup

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
import com.vultisig.wallet.ui.models.BackupPasswordState
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class BackupPasswordRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context,
    private val snackbarFlow: SnackbarFlow,

    private val createVaultBackupFileName: CreateVaultBackupFileNameUseCase,
    private val createVaultBackup: CreateVaultBackupUseCase,
    private val isFileExtensionValid: IsVaultBackupFileExtensionValidUseCase,

    private val vaultRepository: VaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val mapVaultToProto: MapVaultToProto,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.BackupPassword>()

    private val vaultId = args.vaultId
    private val vaultType = args.vaultType

    private val vault = MutableStateFlow<Vault?>(null)

    val state = MutableStateFlow(BackupPasswordState())

    val createDocumentRequestFlow = MutableSharedFlow<String>()

    init {
        viewModelScope.launch {
            vault.value = vaultRepository.get(vaultId)
                ?: error("Vault with id $vaultId not found")
        }
    }

    fun dismissError() {
        state.update { it.copy(error = null) }
    }

    fun handleWriteFilePermissionStatus(isGranted: Boolean) {
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

    fun saveVaultIntoUri(uri: Uri) {
        if (isFileExtensionValid(uri)) {
            val vault = vault.value
                ?: error("Vault is empty, but it should've been filled when name was generated")
            val vaultBackupData = createVaultBackup(
                mapVaultToProto(vault),
                null
            ) ?: error("Vault backup data is empty on generation")

            val isSuccess = context.saveContentToUri(uri, vaultBackupData)

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

    fun backupWithoutPassword() {
        viewModelScope.launch {
            val vault = vault.filterNotNull().first()
            createDocumentRequestFlow.emit(createVaultBackupFileName(vault))
        }
    }

    fun backupWithPassword() {
        viewModelScope.launch {
            navigator.route(
                Route.BackupPassword(
                    vaultId = vaultId,
                    vaultType = vaultType
                )
            )
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

}