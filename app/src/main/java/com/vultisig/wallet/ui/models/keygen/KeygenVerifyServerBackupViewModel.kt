package com.vultisig.wallet.ui.models.keygen

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class KeygenVerifyServerBackupUiModel(
    val codeError: UiText? = null,
)

@HiltViewModel
internal class KeygenVerifyServerBackupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,

    private val vultiSignerRepo: VultiSignerRepository,
    private val vaultMetadataRepo: VaultMetadataRepo,
    private val vaultRepo: VaultRepository,
) : ViewModel() {

    val state = MutableStateFlow(KeygenVerifyServerBackupUiModel())

    val codeFieldState = TextFieldState()

    private val vaultId: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_ID])
    private val shouldSuggestBackup: Boolean = savedStateHandle[
        Destination.VerifyServerBackup.ARG_SHOULD_SUGGEST_BACKUP] ?: false

    fun proceed() {
        val code = codeFieldState.text.toString()

        viewModelScope.launch {
            val vault = vaultRepo.get(vaultId) ?: return@launch

            setError(null)

            val isCodeValid = vultiSignerRepo.isBackupCodeValid(
                publicKeyEcdsa = vault.pubKeyECDSA,
                code = code,
            )

            if (isCodeValid) {
                vaultMetadataRepo.setServerBackupVerified(vaultId)

                if (shouldSuggestBackup) {
                    navigator.route(
                        route = Route.BackupVault(
                            vaultId = vaultId,
                            vaultType = null,
                        ),
                        opts = NavigationOptions(
                            popUpTo = Destination.VerifyServerBackup.STATIC_ROUTE,
                        )
                    )
                } else {
                    navigator.navigate(
                        dst = Destination.Home(),
                        opts = NavigationOptions(clearBackStack = true)
                    )
                }
            } else {
                setError(UiText.StringResource(R.string.keygen_verify_server_backup_invalid_code))
            }
        }
    }

    private fun setError(error: UiText?) {
        state.update {
            it.copy(
                codeError = error,
            )
        }
    }

}