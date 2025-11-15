package com.vultisig.wallet.ui.models.keygen

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
import com.vultisig.wallet.data.usecases.backup.toMimeType
import com.vultisig.wallet.ui.navigation.BackupPasswordTypeNavType
import com.vultisig.wallet.ui.navigation.BackupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.BackupVault.BackupPasswordType
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.reflect.typeOf

internal data class BackupVaultState(
    val error: UiText? = null,
)

@HiltViewModel
internal class BackupVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    private val createVaultBackupFileName: CreateVaultBackupFileNameUseCase,
    private val isFileExtensionValid: IsVaultBackupFileExtensionValidUseCase,
    private val createVaultBackup: CreateVaultBackupUseCase,
    private val mapVaultToProto: MapVaultToProto,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val snackbarFlow: SnackbarFlow,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.BackupVault>(
        mapOf(typeOf<BackupPasswordType>() to BackupPasswordTypeNavType)
    )
    val vultiServerPasswordType =
        args.passwordType as? BackupPasswordType.VultiServerPassword


    private val vault = MutableStateFlow<Vault?>(null)

    val createDocumentRequestFlow = MutableSharedFlow<String>()

    val state = MutableStateFlow(BackupVaultState())

    fun backup() {
        viewModelScope.launch {
            when (args.passwordType) {
                BackupPasswordType.UserSelectionPassword -> {
                    navigateToPasswordRequestBackup()
                }

                is BackupPasswordType.VultiServerPassword -> {
                    backupWithVultiServerPassword()
                }
            }
        }
    }

    private suspend fun backupWithVultiServerPassword() {
        val vault = requireNotNull(vaultRepository.get(args.vaultId))
        this@BackupVaultViewModel.vault.value = vault
        val fileName = createVaultBackupFileName(requireNotNull(vault))
        createDocumentRequestFlow.emit(fileName)
    }

    private suspend fun navigateToPasswordRequestBackup() {
        navigator.route(
            Route.BackupPasswordRequest(
                vaultId = args.vaultId,
                backupType = BackupType.CurrentVault(
                    vaultType = args.vaultType,
                    action = args.action,
                )
            )
        )
    }

    fun saveContentToUriResult(uri: Uri, mimeType: String) {
        viewModelScope.launch {
            val password = requireNotNull(vultiServerPasswordType?.password)
            if (isFileExtensionValid(uri, mimeType.toMimeType())) {
                val isSuccess = backupCurrentVault(password, uri)
                completeBackupVault(isSuccess)
            } else {
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

    private suspend fun backupCurrentVault(password: String, uri: Uri): Boolean {
        val vault = vault.value
        if (vault == null) {
            viewModelScope.launch {
                showError()
            }
            return false
        }

        val backup = createVaultBackup(
            mapVaultToProto(vault),
            password,
        )

        if (backup == null) {
            viewModelScope.launch {
                showError()
            }
            return false
        }

        return context.saveContentToUri(uri, backup)
    }

    private suspend fun showError() {
        snackbarFlow.showMessage(
            context.getString(R.string.vault_settings_error_backup_file)
        )
    }


    private fun completeBackupVault(backupSuccess: Boolean) {
        viewModelScope.launch {

            val backupType = BackupType.CurrentVault(
                vaultType = args.vaultType,
                action = args.action,
            )
            val vaultId = args.vaultId

            if (backupSuccess) {
                withContext(Dispatchers.IO) {
                    vaultDataStoreRepository.setBackupStatus(args.vaultId, true)
                }

                snackbarFlow.showMessage(
                    context.getString(
                        R.string.vault_settings_success_backup_message
                    )
                )

                navigator.route(
                    Route.VaultConfirmation(
                        vaultId = vaultId,
                        vaultType = requireNotNull(backupType.vaultType),
                        action = backupType.action,
                    )
                )
            } else {
                navigateToPasswordRequestBackup()
            }
        }
    }


}