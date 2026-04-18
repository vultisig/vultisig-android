package com.vultisig.wallet.ui.models

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.annotation.StringRes
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
import com.vultisig.wallet.data.usecases.MalformedVaultException
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCase
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.data.usecases.WrongPasswordException
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
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class ImportFileState(
    val fileUri: Uri? = null,
    val fileName: String? = null,
    val fileContent: String? = null,
    val error: UiText? = null,
    val showPasswordPrompt: Boolean = false,
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
        val password = passwordTextFieldState.text.toString()
        val content = uiModel.value.fileContent?.takeUnless { it.isBlank() } ?: return
        viewModelScope.launch {
            val result = saveToDb(content, password)
            if (result != SaveResult.WrongPassword) hidePasswordPromptDialog()
            renderResult(result)
        }
    }

    private suspend fun parseFileContent() {
        val content = uiModel.value.fileContent ?: return
        val result = saveToDb(content, null)
        if (result == SaveResult.WrongPassword) {
            // First pass with no password means the file needs one — pop the prompt.
            uiModel.update { it.copy(showPasswordPrompt = true, passwordErrorHint = null) }
        } else {
            renderResult(result)
        }
    }

    private fun renderResult(result: SaveResult) =
        when (result) {
            SaveResult.Success -> showSuccessImport()
            SaveResult.Duplicate -> showDuplicateError()
            SaveResult.WrongPassword -> showErrorHint()
            SaveResult.Malformed -> showMalformedError()
            SaveResult.Failed -> showGenericError()
        }

    private suspend fun saveToDb(fileContent: String, password: String?): SaveResult =
        try {
            insertVaultToDb(parseVaultFromString(fileContent, password))
            SaveResult.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: DuplicateVaultException) {
            Timber.d(e, "Vault already exists")
            SaveResult.Duplicate
        } catch (e: SQLiteConstraintException) {
            Timber.w(e, "Vault already exists (SQLite)")
            SaveResult.Duplicate
        } catch (e: WrongPasswordException) {
            Timber.d(e, "Wrong or missing vault password")
            SaveResult.WrongPassword
        } catch (e: MalformedVaultException) {
            Timber.w(e, "Vault file is malformed")
            SaveResult.Malformed
        } catch (e: Exception) {
            Timber.e(e, "Vault import failed unexpectedly")
            SaveResult.Failed
        }

    private fun showSuccessImport() =
        showSnackBarMessage(
            message = context.getString(R.string.import_file_screen_success_import),
            type = SnackbarType.Success,
        )

    private fun showSnackBarMessage(message: String, type: SnackbarType) {
        viewModelScope.launch { snackBarFlow.showMessage(message = message, type = type) }
    }

    private fun showDuplicateError() =
        showError(R.string.import_file_screen_duplicate_vault, FailurePolicy.DropFile)

    // Unusable file — drop it so the user picks another. For zips, also remove the bad entry
    // from zipOutputs so re-tapping doesn't re-enter the same doomed file.
    private fun showMalformedError() =
        showError(R.string.import_file_not_supported, FailurePolicy.DropFile)

    // The file looks valid; a post-decrypt step failed. Keep the file so the user can retry.
    private fun showGenericError() =
        showError(R.string.dialog_default_error_body, FailurePolicy.KeepFile)

    private fun showError(@StringRes resId: Int, policy: FailurePolicy) {
        val state = uiModel.value
        if (state.isZip == true) {
            // The archive itself stays usable (fileName/fileUri preserved); only the bad
            // entry is pruned from zipOutputs and its content cleared, so the user can pick
            // another share from the same zip.
            showSnackBarMessage(context.getString(resId), SnackbarType.Error)
            if (policy == FailurePolicy.DropFile) {
                val badContent = state.fileContent
                uiModel.update {
                    it.copy(
                        zipOutputs = it.zipOutputs.filter { entry -> entry.content != badContent },
                        fileContent = null,
                    )
                }
            }
            return
        }
        uiModel.update {
            it.copy(
                error = UiText.StringResource(resId),
                fileName = if (policy == FailurePolicy.DropFile) null else it.fileName,
                fileContent = if (policy == FailurePolicy.DropFile) null else it.fileContent,
            )
        }
    }

    private enum class FailurePolicy {
        DropFile,
        KeepFile,
    }

    private enum class SaveResult {
        Success,
        Duplicate,
        WrongPassword,
        Malformed,
        Failed,
    }

    // saveVault is the point of no return — failure there surfaces as SaveResult.Failed. Every
    // step after runs best-effort so a datastore / network / derivation glitch can't orphan a
    // saved vault or surface as a misleading top-level error.
    private suspend fun insertVaultToDb(vault: Vault) {
        val adjusted = vault.withInferredLibType(uiModel.value.fileName)

        saveVault(adjusted, false)
        runBestEffort("Failed to set backup status") {
            vaultDataStoreRepository.setBackupStatus(adjusted.id, true)
        }
        runBestEffort("Token discovery failed") { discoverToken(adjusted.id, null) }
        if (adjusted.pubKeyMLDSA.isNotBlank()) attachQbtcToken(adjusted)

        finishImport(adjusted)
    }

    private suspend fun attachQbtcToken(vault: Vault) =
        runBestEffort("Failed to add QBTC token") {
            val qbtc = Coins.Qbtc.QBTC
            val (address, pubKey) = chainAccountAddressRepository.getAddress(qbtc, vault)
            vaultRepository.addTokenToVault(
                vault.id,
                qbtc.copy(address = address, hexPublicKey = pubKey),
            )
        }

    private suspend fun finishImport(vault: Vault) {
        if (uiModel.value.isZip != true) {
            navigateToHome(vault)
            return
        }
        val remaining = uiModel.value.zipOutputs.filter { it.content != uiModel.value.fileContent }
        if (remaining.isEmpty()) {
            navigateToHome(vault)
        } else {
            uiModel.update {
                it.copy(zipOutputs = remaining, canNavigateToHome = true, activeVault = vault)
            }
        }
    }

    // The vault is already saved; navigator glitches mustn't surface as an import failure.
    private suspend fun navigateToHome(vault: Vault) {
        runBestEffort("Failed to navigate home after import; vault is saved") {
            navigator.route(
                Route.Home(openVaultId = vault.id),
                opts = NavigationOptions(clearBackStack = true),
            )
        }
    }

    private suspend inline fun runBestEffort(message: String, block: () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, message)
        }
    }

    // Older DKLS backups were sometimes persisted with libType=GG20. Recover the real type
    // from the share-NofM filename convention, but leave KeyImport vaults alone — they also
    // use that naming and must keep their declared libType.
    private fun Vault.withInferredLibType(fileName: String?): Vault =
        if (libType == SigningLibType.GG20 && fileName?.contains(SHARE_FILENAME_REGEX) == true) {
            copy(libType = SigningLibType.DKLS)
        } else {
            this
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
        uri ?: return
        viewModelScope.launch {
            val fileName = uri.fileName(context)?.takeUnless { it.isBlank() }
            if (fileName == null) {
                resetPickerWithError()
                return@launch
            }
            val isZip = uri.isValidZipFile(context)
            val hasAllowedExtension = File(fileName).extension in FILE_ALLOWED_EXTENSIONS
            when {
                !isZip && !hasAllowedExtension -> resetPickerWithError()
                isZip -> acceptZip(uri, fileName)
                else -> acceptSingleFile(uri, fileName)
            }
        }
    }

    private fun resetPickerWithError() {
        uiModel.update {
            it.copy(
                fileUri = null,
                fileName = null,
                fileContent = null,
                isZip = null,
                error = UiText.StringResource(R.string.import_file_not_supported),
            )
        }
    }

    private suspend fun acceptZip(uri: Uri, fileName: String) {
        val entries = uri.processZip(context)
        uiModel.update {
            it.copy(
                fileUri = uri,
                fileName = fileName,
                isZip = true,
                zipOutputs = entries,
                error =
                    UiText.StringResource(R.string.import_file_not_supported).takeIf {
                        entries.isEmpty()
                    },
            )
        }
    }

    private fun acceptSingleFile(uri: Uri, fileName: String) {
        uiModel.update { it.copy(fileUri = uri, fileName = fileName, error = null, isZip = false) }
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
        uiModel.update { it.copy(isPasswordObfuscated = !it.isPasswordObfuscated) }
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
            val target = state.activeVault?.takeIf { state.canNavigateToHome }
            if (target != null) navigateToHome(target) else navigator.back()
        }
    }

    private companion object {
        private val SHARE_FILENAME_REGEX = "share\\d+of\\d+".toRegex()
    }
}
