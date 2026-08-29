package com.vultisig.wallet.ui.models.keygen

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.mappers.MapVaultToProto
import com.vultisig.wallet.data.mappers.exportableOrNull
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.repositories.AppReviewEvent
import com.vultisig.wallet.data.repositories.InAppReviewRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.recordAndOfferPrompt
import com.vultisig.wallet.data.usecases.CreateVaultBackupUseCase
import com.vultisig.wallet.data.usecases.backup.CreateVaultBackupFileNameUseCase
import com.vultisig.wallet.data.usecases.backup.DeleteBackupDocumentUseCase
import com.vultisig.wallet.data.usecases.backup.FILE_ALLOWED_EXTENSIONS
import com.vultisig.wallet.data.usecases.backup.IsVaultBackupFileExtensionValidUseCase
import com.vultisig.wallet.data.usecases.backup.SaveBackupToUriUseCase
import com.vultisig.wallet.data.usecases.backup.toMimeType
import com.vultisig.wallet.ui.navigation.BackupPasswordTypeNavType
import com.vultisig.wallet.ui.navigation.BackupType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.BackupVault.BackupPasswordType
import com.vultisig.wallet.ui.navigation.Route.VaultInfo
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlin.reflect.typeOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

@HiltViewModel
internal class BackupVaultViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val createVaultBackupFileName: CreateVaultBackupFileNameUseCase,
    private val isFileExtensionValid: IsVaultBackupFileExtensionValidUseCase,
    private val createVaultBackup: CreateVaultBackupUseCase,
    private val mapVaultToProto: MapVaultToProto,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val snackbarFlow: SnackbarFlow,
    private val saveBackupToUri: SaveBackupToUriUseCase,
    private val deleteBackupDocument: DeleteBackupDocumentUseCase,
    private val inAppReviewRepository: InAppReviewRepository,
) : ViewModel() {

    private val args =
        savedStateHandle.toRoute<Route.BackupVault>(
            mapOf(typeOf<BackupPasswordType>() to BackupPasswordTypeNavType)
        )
    val vultiServerPasswordType = args.passwordType as? BackupPasswordType.VultiServerPassword

    private val vault = MutableStateFlow<Vault?>(null)

    val createDocumentRequestFlow = MutableSharedFlow<String>()
    val isFastVault: Boolean = args.vaultType == VaultInfo.VaultType.Fast

    private val _title =
        MutableStateFlow<UiText>(
            if (isFastVault) UiText.StringResource(R.string.backup_save_backup_to_the_cloud)
            else UiText.Empty
        )
    val title: StateFlow<UiText> = _title.asStateFlow()

    init {
        if (!isFastVault) {
            viewModelScope.launch {
                val vault = vaultRepository.get(args.vaultId) ?: return@launch
                val total = vault.signers.size
                val position = vault.getVaultPart().coerceAtLeast(1)
                _title.value =
                    UiText.FormattedText(
                        R.string.vault_setup_save_backup_n_of_n_to_the_cloud,
                        listOf(position, total),
                    )
            }
        }
    }

    fun backup() {
        viewModelScope.launch {
            when (args.passwordType) {
                BackupPasswordType.UserSelectionPassword -> {
                    navigateToPasswordRequestBackup()
                }

                is BackupPasswordType.VultiServerPassword -> {
                    onDeviceBackupClick()
                }
            }
        }
    }

    fun onDeviceBackupClick() {
        viewModelScope.launch { backupWithVultiServerPassword() }
    }

    private suspend fun backupWithVultiServerPassword() {
        // Kept for the export that follows, so it has to carry its keyshares: a vault read while
        // the app is locked comes back without them.
        vaultRepository.awaitKeySharesReadable()
        val vault = requireNotNull(vaultRepository.get(args.vaultId))
        this@BackupVaultViewModel.vault.value = vault
        val fileName = createVaultBackupFileName(vault)
        createDocumentRequestFlow.emit(fileName)
    }

    private suspend fun navigateToPasswordRequestBackup() {
        navigator.route(
            Route.BackupPasswordRequest(
                vaultId = args.vaultId,
                backupType =
                    BackupType.CurrentVault(vaultType = args.vaultType, action = args.action),
            )
        )
    }

    fun saveContentToUriResult(uri: Uri, mimeType: String) {
        viewModelScope.launch {
            val password =
                runCatching { requireNotNull(vultiServerPasswordType?.password) }
                    .getOrElse {
                        navigateToPasswordRequestBackup()
                        return@launch
                    }
            if (isFileExtensionValid(uri, mimeType.toMimeType())) {
                val isSuccess = backupCurrentVault(password, uri)
                // The picker created the document before anything was written to it. Failing
                // without removing it leaves an empty .vult in the user's files that looks like a
                // backup and restores nothing.
                if (!isSuccess) {
                    deleteBackupDocument(uri)
                }
                completeBackupVault(isSuccess)
            } else {
                deleteBackupDocument(uri)
                showError(
                    UiText.FormattedText(
                        R.string.vault_settings_error_extension_backup_file,
                        listOf(FILE_ALLOWED_EXTENSIONS.joinToString(", ")),
                    )
                )
            }
        }
    }

    private suspend fun backupCurrentVault(password: String, uri: Uri): Boolean {
        val vault = vault.value
        if (vault == null) {
            viewModelScope.launch {
                showError(UiText.StringResource(R.string.vault_settings_error_backup_file))
            }
            return false
        }

        val backup =
            withContext(Dispatchers.Default) {
                val proto = mapVaultToProto.exportableOrNull(vault) ?: return@withContext null
                createVaultBackup(proto, password)
            }

        if (backup == null) {
            viewModelScope.launch {
                showError(UiText.StringResource(R.string.vault_settings_error_backup_file))
            }
            return false
        }

        return saveBackupToUri(uri, backup)
    }

    private suspend fun showError(message: UiText) {
        snackbarFlow.showMessage(message)
    }

    private fun completeBackupVault(backupSuccess: Boolean) {
        viewModelScope.launch {
            val backupType =
                BackupType.CurrentVault(
                    vaultType =
                        runCatching { requireNotNull(args.vaultType) }
                            .getOrElse {
                                navigateToPasswordRequestBackup()
                                return@launch
                            },
                    action = args.action,
                )
            val vaultId = args.vaultId

            if (backupSuccess) {
                withContext(Dispatchers.IO) {
                    vaultDataStoreRepository.setBackupStatus(args.vaultId, true)
                }

                // Securing a vault is a genuine "this worked" moment, and the first one per vault
                // is the only one that counts — re-exporting the same share is not a new milestone.
                try {
                    inAppReviewRepository.recordAndOfferPrompt(
                        AppReviewEvent.VaultBackupCompleted(vaultId)
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (e: Exception) {
                    Timber.e(e, "Failed to record in-app review for vault backup")
                }

                snackbarFlow.showMessage(
                    UiText.StringResource(R.string.vault_settings_success_backup_message)
                )

                when (backupType.action) {
                    TssAction.Migrate -> {
                        navigator.route(
                            route = Route.Home(),
                            opts = NavigationOptions(clearBackStack = true),
                        )
                    }

                    else -> {
                        navigator.route(
                            route =
                                Route.VaultBackupSummary(
                                    vaultId = vaultId,
                                    vaultType = requireNotNull(backupType.vaultType),
                                ),
                            opts =
                                NavigationOptions(
                                    popUpToRoute = Route.ChooseVaultType::class,
                                    inclusive = true,
                                ),
                        )
                    }
                }
            } else {
                navigateToPasswordRequestBackup()
            }
        }
    }
}
