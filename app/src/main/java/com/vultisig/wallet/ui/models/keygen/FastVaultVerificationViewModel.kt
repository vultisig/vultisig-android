package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.repositories.vault.TemporaryVaultRepository
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.ui.components.canAuthenticateBiometric
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal enum class VerifyPinState {
    Idle, Loading, Success, Error
}

internal data class VaultBackupState(
    val verifyPinState: VerifyPinState = VerifyPinState.Idle,
    val sentEmailTo: String,
)

private const val FAST_VAULT_VERIFICATION_SUCCESS_DELAY = 400L

@HiltViewModel
internal class FastVaultVerificationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    private val saveVault: SaveVaultUseCase,
    private val vultiSignerRepository: VultiSignerRepository,
    private val temporaryVaultRepository: TemporaryVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.FastVaultVerification>()
    private val email = args.email

    val state = MutableStateFlow(VaultBackupState(sentEmailTo = email))

    val codeFieldState = TextFieldState()

    fun verifyCode() {
        verify()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun changeEmail() {
        viewModelScope.launch {
            navigator.route(
                route = Route.VaultInfo.Name(
                    vaultType = Route.VaultInfo.VaultType.Fast,
                ),
                opts = NavigationOptions(
                    popUpToRoute = Route.Onboarding.VaultBackup::class,
                    inclusive = true,
                )
            )
        }
    }

    private fun verify() {
        viewModelScope.launch {
            val code = codeFieldState.text.toString()

            if (isCodeTemplateValid(code)) {
                updateVerifyState(VerifyPinState.Loading)

                val isCodeValid = vultiSignerRepository.isBackupCodeValid(
                    publicKeyEcdsa = args.pubKeyEcdsa,
                    code = code,
                )

                if (isCodeValid) {
                    updateVerifyState(VerifyPinState.Success)

                    val vaultId = args.vaultId
                    val vault = temporaryVaultRepository.getById(vaultId)

                    saveVault(vault.vault, false)

                    vault.hint?.let {
                        vaultDataStoreRepository.setFastSignHint(
                            vaultId = vaultId, hint = it
                        )
                    }

                    if (context.canAuthenticateBiometric()) {
                        vaultPasswordRepository.savePassword(vaultId, vault.password)
                    }

                    delay(FAST_VAULT_VERIFICATION_SUCCESS_DELAY)
                    navigator.route(
                        route = Route.BackupVault(
                            vaultId = args.vaultId,
                            vaultType = VaultType.Fast,
                        )
                    )
                } else {
                    updateVerifyState(VerifyPinState.Error)
                }
            } else {
                updateVerifyState(VerifyPinState.Error)
            }
        }
    }

    private fun isCodeTemplateValid(code: String) =
        code.isDigitsOnly() && code.length == FAST_VAULT_VERIFICATION_CODE_LENGTH

    private fun updateVerifyState(verifyState: VerifyPinState) {
        state.update {
            it.copy(verifyPinState = verifyState)
        }
    }

    companion object {
        const val FAST_VAULT_VERIFICATION_CODE_LENGTH = 4
    }

}
