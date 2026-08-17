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
    /**
     * What the screen says below its title: the standing instruction until a backup has been tried,
     * and what came of that attempt afterwards.
     */
    @StringRes val message: Int = R.string.passcode_key_unavailable_message,
    val isRestoring: Boolean = false,
    val isPasswordPromptVisible: Boolean = false,
    val isPasswordObfuscated: Boolean = true,
    @StringRes val passwordError: Int? = null,
)

/**
 * Restores vaults from their backup file while the gate is closed over an unreadable keystore.
 *
 * The import cannot be routed to from here — the import screen draws in the window this one covers
 * — so the file is read and stored on the spot, and the gate is asked to resolve again afterwards.
 * It opens once no sealed keyshare is left on the device, which takes a backup per vault.
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

    /** The shares the picked file holds, kept while a password for them is being asked for. */
    private var picked: List<AppZipEntry> = emptyList()

    fun onBackupPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.safeLaunch {
            val shares = read(uri)
            if (shares.isEmpty()) {
                report(R.string.import_file_not_supported)
                return@safeLaunch
            }
            picked = shares
            // Every share in an archive was exported together, under one password, and the prompt
            // below opens on whatever the field holds.
            passwordTextFieldState.clearText()
            restore(password = null)
        }
    }

    fun onPasswordEntered() {
        if (picked.isEmpty()) return
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
     * An archive is what "back up all vaults" writes, and the gate needs a share per vault before
     * it opens — so refusing one here would name the only file the user has as the wrong file. Each
     * share keeps its own name, which is what tells a mislabelled older backup from a GG20 one; see
     * [StoreImportedVaultUseCase].
     */
    private suspend fun read(uri: Uri): List<AppZipEntry> {
        val shares =
            if (uriFileReader.isValidZip(uri)) {
                uriFileReader.extractZipEntries(uri).entries
            } else {
                val content = uriFileReader.readContent(uri) ?: return emptyList()
                listOf(AppZipEntry(name = uriFileReader.readName(uri).orEmpty(), content = content))
            }
        return shares.filter { it.content.isNotBlank() }
    }

    /**
     * Stores every share the picked file holds, and asks the gate to resolve again if any landed.
     *
     * One outcome for the whole file rather than one per share: an archive holds a share per vault,
     * and the ones it carries for vaults this device never lost are duplicates by design.
     */
    private suspend fun restore(password: String?) {
        // Deriving an address per default chain takes seconds. Without this, the second tap of a
        // double tap starts a second restore of the same file, which finds the row the first one
        // just wrote and reports a duplicate over its success. Read and set before this suspends,
        // on a scope that dispatches to one thread, so the two cannot both pass.
        if (state.value.isRestoring) return
        _state.update {
            it.copy(message = R.string.passcode_key_unavailable_message, isRestoring = true)
        }
        try {
            var restored: Vault? = null
            var refusal: Int? = null
            for (share in picked) {
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
        if (restored == null) {
            report(refusal ?: R.string.import_file_not_supported)
            return
        }
        picked = emptyList()
        passcodeRepository.retry()
        // Only the last vault's restore takes the gate down. Said after the state has settled, so
        // it
        // is never claimed over a gate that is already lifting.
        if (passcodeRepository.state.value == PasscodeState.KeyUnavailable) {
            report(R.string.passcode_key_unavailable_restored)
        }
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
