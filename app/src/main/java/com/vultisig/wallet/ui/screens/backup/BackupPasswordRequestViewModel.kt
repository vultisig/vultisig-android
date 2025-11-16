package com.vultisig.wallet.ui.screens.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.data.common.saveContentToUri
import com.vultisig.wallet.data.mappers.MapVaultToProto
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.CreateVaultBackupUseCase
import com.vultisig.wallet.data.usecases.backup.CreateVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.CreateZipVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.FILE_ALLOWED_EXTENSIONS
import com.vultisig.wallet.data.usecases.backup.IsVaultBackupFileExtensionValidUseCase
import com.vultisig.wallet.data.usecases.backup.MimeType
import com.vultisig.wallet.data.usecases.backup.MimeType.*
import com.vultisig.wallet.data.usecases.backup.toMimeType
import com.vultisig.wallet.ui.navigation.BackupType
import com.vultisig.wallet.ui.navigation.BackupTypeNavType
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
import kotlin.reflect.typeOf

internal data class BackupPasswordState(
    val mimeType: MimeType = OCTET_STREAM,
    val isMoreInfoVisible: Boolean = false,
    val passwordErrorMessage: UiText? = null,
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isNextButtonEnabled: Boolean = false,
    val error: UiText? = null,
)

@HiltViewModel
internal class BackupPasswordRequestViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context,
    private val snackbarFlow: SnackbarFlow,

    private val createVaultBackupFileName: CreateVaultBackupFileNameUseCase,
    private val createZipVaultsBackupFileName: CreateZipVaultBackupFileNameUseCase,
    private val createVaultBackup: CreateVaultBackupUseCase,
    private val isFileExtensionValid: IsVaultBackupFileExtensionValidUseCase,

    private val vaultRepository: VaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val mapVaultToProto: MapVaultToProto,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.BackupPasswordRequest>(
        typeMap = mapOf(
            typeOf<BackupType>() to BackupTypeNavType
        )
    )

    private val vaultId = args.vaultId
    private val backupType = args.backupType

    private val vault = MutableStateFlow<Vault?>(null)
    private var vaults = listOf<Vault>()


    val state = MutableStateFlow(BackupPasswordState(
        mimeType = when(backupType){
            BackupType.AllVaults -> ZIP
            is BackupType.CurrentVault -> OCTET_STREAM
        }
    ))

    val createDocumentRequestFlow = MutableSharedFlow<String>()

    init {
        viewModelScope.launch {
            vault.value = vaultRepository.get(vaultId)
                ?: error("Vault with id $vaultId not found")
            vaults = vaultRepository.getAll()
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

    fun saveVaultIntoUri(uri: Uri, mimeType: String) {
        viewModelScope.launch {
            if (isFileExtensionValid(uri = uri, mimeType = mimeType.toMimeType())) {
                val isSuccess = backup(uri)
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

    private suspend fun backup(uri: Uri): Boolean {
        val isSuccess = when (backupType) {
            BackupType.AllVaults -> {
                backupAllVaults(uri)
            }

            is BackupType.CurrentVault -> {
                backupCurrentVault(uri)
            }
        }
        return isSuccess
    }

    private suspend fun backupCurrentVault(uri: Uri): Boolean {
        val vault = vault.value
            ?: return false
        val vaultBackupData = createVaultBackup(
            mapVaultToProto(vault),
            null
        ) ?: return false

        return context.saveContentToUri(uri, vaultBackupData)
    }

    private suspend fun backupAllVaults(uri: Uri): Boolean {
        val content = vaults.map { vault ->
            val vaultBackupData = createVaultBackup(
                mapVaultToProto(vault),
                null
            ) ?: return false
            val fileName = createVaultBackupFileName(vault)
            AppZipEntry(fileName, vaultBackupData)
        }

        return context.saveContentToUri(uri, content)
    }


    private fun completeBackupVault(backupSuccess: Boolean) {
        viewModelScope.launch {
            if (backupSuccess) {
                updateBackupStatus()

                snackbarFlow.showMessage(
                    context.getString(
                        R.string.vault_settings_success_backup_message
                    )
                )

                if (backupType is BackupType.CurrentVault && backupType.vaultType != null) {
                    navigator.route(
                        Route.VaultConfirmation(
                            vaultId = vaultId,
                            vaultType = backupType.vaultType,
                            action = backupType.action,
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

    private suspend fun updateBackupStatus() {
        when (backupType) {
            BackupType.AllVaults -> {
                vaults.forEach { vault ->
                    vaultDataStoreRepository.setBackupStatus(vault.id, true)
                }
            }

            is BackupType.CurrentVault -> {
                vaultDataStoreRepository.setBackupStatus(vaultId, true)
            }
        }
    }

    fun backupWithoutPassword() {
        viewModelScope.launch {
            when (backupType) {
                BackupType.AllVaults -> {
                    createDocumentRequestFlow.emit(createZipVaultsBackupFileName(vaults))
                }

                is BackupType.CurrentVault -> {
                    val vault = vault.filterNotNull().first()
                    createDocumentRequestFlow.emit(createVaultBackupFileName(vault))
                }
            }
        }
    }

    fun backupWithPassword() {
        viewModelScope.launch {
            navigator.route(
                Route.BackupPassword(
                    vaultId = vaultId,
                    backupType = backupType
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