package com.vultisig.wallet.ui.models

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.compose.foundation.text.input.TextFieldState
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.data.common.fileContent
import com.vultisig.wallet.data.common.fileName
import com.vultisig.wallet.data.common.isValidZipFile
import com.vultisig.wallet.data.common.processZip
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.data.usecases.DuplicateVaultException
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCase
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.data.usecases.backup.FILE_ALLOWED_EXTENSIONS
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class ImportFileState(
    val fileUri: Uri? = null,
    val fileName: String? = null,
    val fileContent: String? = null,
    val error: UiText? = null,
    val showPasswordPrompt: Boolean = false,
    val password: String? = null,
    val isPasswordObfuscated: Boolean = true,
    val passwordErrorHint: UiText? = null,
    val isZip: Boolean? = null,
    val zipOutputs: List<AppZipEntry> = emptyList(),
    val canNavigateToHome: Boolean = false,
    val activeVault: Vault? = null,
)

internal val FILE_ALLOWED_MIME_TYPES = arrayOf("application/*", "text/plain")

@HiltViewModel
internal class ImportFileViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    @param:ApplicationContext private val context: Context,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val saveVault: SaveVaultUseCase,
    private val parseVaultFromString: ParseVaultFromStringUseCase,
    private val discoverToken: DiscoverTokenUseCase,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val snackBarFlow: SnackbarFlow,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.ImportVault>()

    val uiModel = MutableStateFlow(ImportFileState())

    val passwordTextFieldState = TextFieldState()

    init {
        args.uri?.toUri()?.let { fetchFileName(it) }
    }

    fun hidePasswordPromptDialog() {
        uiModel.update { it.copy(showPasswordPrompt = false) }
    }

    fun decryptVaultData() {
        val key: String = passwordTextFieldState.text.toString()
        val vaultFileContent = uiModel.value.fileContent
        if (!vaultFileContent.isNullOrBlank()) {
            viewModelScope.launch {
                when (saveToDb(vaultFileContent, key)) {
                    SaveResult.Success -> {
                        hidePasswordPromptDialog()
                        showSuccessImport()
                    }
                    SaveResult.Duplicate -> {
                        hidePasswordPromptDialog()
                        showDuplicateError()
                    }
                    SaveResult.Failed -> showErrorHint()
                }
            }
        }
    }

    private suspend fun parseFileContent() {
        val fileContent = uiModel.value.fileContent ?: return
        when (saveToDb(fileContent, null)) {
            SaveResult.Success -> showSuccessImport()
            SaveResult.Duplicate -> showDuplicateError()
            SaveResult.Failed ->
                uiModel.update { it.copy(showPasswordPrompt = true, passwordErrorHint = null) }
        }
    }

    private suspend fun saveToDb(fileContent: String, password: String?): SaveResult =
        try {
            val vault =
                withContext(Dispatchers.Default) { parseVaultFromString(fileContent, password) }
            insertVaultToDb(vault)
            SaveResult.Success
        } catch (e: DuplicateVaultException) {
            Timber.e(e)
            SaveResult.Duplicate
        } catch (e: SQLiteConstraintException) {
            Timber.e(e)
            SaveResult.Duplicate
        } catch (e: Exception) {
            Timber.e(e)
            SaveResult.Failed
        }

    private fun showSuccessImport() {
        showSnackBarMessage(
            message = context.getString(R.string.import_file_screen_success_import),
            type = SnackbarType.Success,
        )
    }

    private fun showSnackBarMessage(message: String, type: SnackbarType) {
        viewModelScope.launch { snackBarFlow.showMessage(message = message, type = type) }
    }

    private fun showDuplicateError() {
        val isZip = uiModel.value.isZip
        if (isZip == true) {
            showSnackBarMessage(
                message = context.getString(R.string.import_file_screen_duplicate_vault),
                type = SnackbarType.Error,
            )
        } else {
            uiModel.update {
                it.copy(
                    error = UiText.StringResource(R.string.import_file_screen_duplicate_vault),
                    fileName = null,
                    fileContent = null,
                )
            }
        }
    }

    private enum class SaveResult {
        Success,
        Duplicate,
        Failed,
    }

    private suspend fun insertVaultToDb(vault: Vault) {
        // if the backup didn't set libtype correctly , then we need a way to override it manually
        // when the backup file has share\d+of\d+ in the filename, then it's a DKLS vault
        // Only apply this heuristic when libType is the default GG20 (old backups).
        // KeyImport vaults also use "share" filenames but must keep their libType.
        val regex = "share\\d+of\\d+".toRegex()
        val adjustedVault =
            if (
                vault.libType == SigningLibType.GG20 &&
                    uiModel.value.fileName?.contains(regex) == true
            ) {
                vault.copy(libType = SigningLibType.DKLS)
            } else {
                vault
            }
        saveVault(adjustedVault, false)
        vaultDataStoreRepository.setBackupStatus(adjustedVault.id, true)
        discoverToken(adjustedVault.id, null)

        if (adjustedVault.pubKeyMLDSA.isNotBlank()) {
            val qbtcToken = Coins.Qbtc.QBTC
            val (address, pubKey) =
                chainAccountAddressRepository.getAddress(qbtcToken, adjustedVault)
            vaultRepository.addTokenToVault(
                adjustedVault.id,
                qbtcToken.copy(address = address, hexPublicKey = pubKey),
            )
        }
        if (uiModel.value.isZip == true) {
            val updatedZipOutput =
                uiModel.value.zipOutputs.filter { it.content != uiModel.value.fileContent }
            if (updatedZipOutput.isEmpty()) {
                navigateToHome(vault = adjustedVault)
            } else {
                uiModel.update {
                    it.copy(
                        zipOutputs = updatedZipOutput,
                        canNavigateToHome = true,
                        activeVault = adjustedVault,
                    )
                }
            }
            return
        }

        navigateToHome(adjustedVault)
    }

    private suspend fun navigateToHome(vault: Vault) {
        navigator.route(
            Route.Home(openVaultId = vault.id),
            opts = NavigationOptions(clearBackStack = true),
        )
    }

    fun saveFileToAppDir() {
        val uri = uiModel.value.fileUri ?: return
        viewModelScope.launch {
            val fileContent = uri.fileContent(context)
            uiModel.update { it.copy(fileContent = fileContent) }
            parseFileContent()
        }
    }

    fun fetchFileName(uri: Uri?) {
        viewModelScope.launch {
            if (uri == null) return@launch
            val fileName = uri.fileName(context = context)
            if (fileName.isNullOrBlank()) {
                uiModel.update {
                    it.copy(
                        fileUri = null,
                        fileName = null,
                        fileContent = null,
                        isZip = null,
                        error = UiText.StringResource(R.string.import_file_not_supported),
                    )
                }
                return@launch
            }
            val file = File(fileName)
            val ext = file.extension
            val isZipFile = uri.isValidZipFile(context = context)
            if (!FILE_ALLOWED_EXTENSIONS.contains(ext) && !isZipFile) {
                uiModel.update {
                    it.copy(
                        fileUri = null,
                        fileName = null,
                        fileContent = null,
                        isZip = null,
                        error = UiText.StringResource(R.string.import_file_not_supported),
                    )
                }
            } else if (isZipFile) {
                val zipOutput = uri.processZip(context = context)
                val error =
                    UiText.StringResource(R.string.import_file_not_supported).takeIf {
                        zipOutput.isEmpty()
                    }
                uiModel.update {
                    it.copy(
                        fileUri = uri,
                        fileName = fileName,
                        isZip = true,
                        zipOutputs = zipOutput,
                        error = error,
                    )
                }
            } else {
                uiModel.update {
                    it.copy(fileUri = uri, fileName = fileName, error = null, isZip = false)
                }
            }
        }
    }

    private fun showErrorHint() {
        uiModel.update {
            it.copy(
                passwordErrorHint =
                    UiText.StringResource(R.string.import_file_screen_password_error)
            )
        }
    }

    fun togglePasswordVisibility() {
        val passwordVisibility = uiModel.value.isPasswordObfuscated
        uiModel.update { it.copy(isPasswordObfuscated = !passwordVisibility) }
    }

    fun importVult(zipOutput: AppZipEntry) {
        viewModelScope.launch {
            uiModel.update { it.copy(fileContent = zipOutput.content) }
            parseFileContent()
        }
    }

    fun back() {
        viewModelScope.launch {
            val state = uiModel.value
            if (state.canNavigateToHome) {
                val activeVault = state.activeVault
                activeVault?.run { navigateToHome(this) } ?: navigator.back()
            } else navigator.back()
        }
    }
}
