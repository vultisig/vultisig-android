package com.vultisig.wallet.ui.models.passcode

import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.DefaultDispatcher
import com.vultisig.wallet.data.common.AppZipContents
import com.vultisig.wallet.data.common.AppZipEntry
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.passcode.PasscodeRepository
import com.vultisig.wallet.data.passcode.PasscodeState
import com.vultisig.wallet.data.usecases.DuplicateVaultException
import com.vultisig.wallet.data.usecases.MalformedVaultException
import com.vultisig.wallet.data.usecases.ParseVaultFromStringUseCase
import com.vultisig.wallet.data.usecases.StoreImportedVaultUseCase
import com.vultisig.wallet.data.usecases.WrongPasswordException
import com.vultisig.wallet.data.usecases.file.UriFileReaderUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import timber.log.Timber

@Immutable
internal data class KeyshareRecoveryUiModel(
    /** The standing instruction until a backup has been tried, then what came of that attempt. */
    @StringRes val message: Int = R.string.passcode_key_unavailable_message,
    val isRestoring: Boolean = false,
    val isPasswordPromptVisible: Boolean = false,
    val isPasswordObfuscated: Boolean = true,
    @StringRes val passwordError: Int? = null,
)

/**
 * Restores vaults from their backup file while the gate is closed over an unreadable keystore.
 *
 * The import screen cannot be routed to — it draws in the window this one covers — so the file is
 * read and stored here, then the gate is asked to resolve again. It opens once no sealed keyshare
 * is left on the device, which takes a backup per vault.
 */
@HiltViewModel
internal class KeyshareRecoveryViewModel
@Inject
constructor(
    private val uriFileReader: UriFileReaderUseCase,
    private val parseVaultFromString: ParseVaultFromStringUseCase,
    private val storeImportedVault: StoreImportedVaultUseCase,
    private val passcodeRepository: PasscodeRepository,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(KeyshareRecoveryUiModel())
    val state: StateFlow<KeyshareRecoveryUiModel> = _state.asStateFlow()

    val passwordTextFieldState = TextFieldState()

    private var picked = AppZipContents(entries = emptyList(), isComplete = true)

    fun onBackupPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.safeLaunch {
            picked = read(uri)
            // Each file has its own password, and the prompt opens on whatever the field holds.
            passwordTextFieldState.clearText()
            restore(password = null)
        }
    }

    fun onPasswordEntered() {
        if (picked.entries.isEmpty()) return
        viewModelScope.safeLaunch { restore(passwordTextFieldState.text.toString()) }
    }

    fun onPasswordPromptDismissed() {
        _state.update { it.copy(isPasswordPromptVisible = false, passwordError = null) }
    }

    fun onPasswordVisibilityToggled() {
        _state.update { it.copy(isPasswordObfuscated = !it.isPasswordObfuscated) }
    }

    fun onRetry() {
        viewModelScope.safeLaunch { passcodeRepository.retry() }
    }

    /**
     * The vault shares [uri] holds: the entries of a multi-vault archive, or the one file itself.
     *
     * "Back up all vaults" writes an archive and the gate needs a share per vault, so refusing one
     * would name the only file the user has as the wrong file. Each share keeps its own name, which
     * is what [StoreImportedVaultUseCase] reads to correct a mislabelled older backup.
     */
    private suspend fun read(uri: Uri): AppZipContents {
        if (uriFileReader.isValidZip(uri)) {
            val contents = uriFileReader.extractZipEntries(uri)
            return contents.copy(entries = contents.entries.filter { it.content.isNotBlank() })
        }
        val content = uriFileReader.readContent(uri)?.takeUnless { it.isBlank() }
        val name = uriFileReader.readName(uri).orEmpty()
        return AppZipContents(
            entries = listOfNotNull(content?.let { AppZipEntry(name = name, content = it) }),
            isComplete = true,
        )
    }

    /**
     * Stores every share the picked file holds, and asks the gate to resolve again if any landed.
     *
     * Parsed through before anything is stored, so a share needing a password cannot leave the ones
     * ahead of it stored with nothing said about them. One outcome for the whole file, too: the
     * shares an archive carries for vaults this device never lost are duplicates by design.
     */
    private suspend fun restore(password: String?) {
        // Read and set before this suspends, on a scope that dispatches to one thread, so a double
        // tap cannot start a second restore that finds the row the first just wrote and reports a
        // duplicate over its success.
        if (state.value.isRestoring) return
        _state.update {
            it.copy(message = R.string.passcode_key_unavailable_message, isRestoring = true)
        }
        try {
            val parsed = mutableListOf<Pair<AppZipEntry, Vault>>()
            var refusal: Int? = null
            for (share in picked.entries) {
                val vault =
                    try {
                        withContext(defaultDispatcher) {
                            parseVaultFromString(share.content, password)
                        }
                    } catch (e: WrongPasswordException) {
                        // Asked once for the whole file, since one password covers all of it.
                        Timber.d(e, "Wrong or missing backup password")
                        promptForPassword(isRetry = password != null)
                        return
                    } catch (e: MalformedVaultException) {
                        Timber.w(e, "Backup share is malformed")
                        refusal = refusal ?: R.string.import_file_not_supported
                        continue
                    }
                parsed += share to vault
            }

            var restored: Vault? = null
            for ((share, vault) in parsed) {
                when (val outcome = store(vault, share.name)) {
                    null -> restored = vault
                    else -> refusal = refusal ?: outcome
                }
            }
            settle(restored, refusal)
        } finally {
            _state.update { it.copy(isRestoring = false) }
        }
    }

    /** Stores [vault], answering null when it landed and the message to show when it did not. */
    private suspend fun store(vault: Vault, fileName: String): Int? =
        try {
            storeImportedVault(vault, fileName)
            null
        } catch (e: CancellationException) {
            throw e
        } catch (e: DuplicateVaultException) {
            Timber.d(e, "Backup share does not restore a vault this device is missing")
            R.string.import_file_screen_duplicate_vault
        } catch (e: Exception) {
            Timber.e(e, "Restoring from a backup failed unexpectedly")
            R.string.dialog_default_error_body
        }

    private suspend fun settle(restored: Vault?, @StringRes refusal: Int?) {
        val wasComplete = picked.isComplete
        if (restored != null) picked = AppZipContents(entries = emptyList(), isComplete = true)
        // Asked whatever the outcome: a store can throw after the replacement has committed, and
        // the gate would then stay closed over a vault that is in fact restored.
        passcodeRepository.retry()
        if (passcodeRepository.state.value != PasscodeState.KeyUnavailable) return
        report(
            when {
                // A truncated archive holds shares that were never read, so this — not a missing
                // backup — is what the user has to act on.
                !wasComplete -> R.string.import_file_zip_incomplete
                restored != null -> R.string.passcode_key_unavailable_restored
                else -> refusal ?: R.string.import_file_not_supported
            }
        )
    }

    private fun promptForPassword(isRetry: Boolean) {
        _state.update {
            it.copy(
                isPasswordPromptVisible = true,
                passwordError = R.string.import_file_screen_password_error.takeIf { _ -> isRetry },
            )
        }
    }

    private fun report(@StringRes message: Int) {
        _state.update {
            it.copy(message = message, isPasswordPromptVisible = false, passwordError = null)
        }
    }
}
