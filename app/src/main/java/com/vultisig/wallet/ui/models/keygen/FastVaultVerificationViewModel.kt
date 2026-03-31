package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.core.text.isDigitsOnly
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.TemporaryVaultRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.data.usecases.fast.VerifyFastVaultBackupCodeUseCase
import com.vultisig.wallet.ui.components.canAuthenticateBiometric
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.BackupVault.BackupPasswordType
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import timber.log.Timber

internal enum class VerifyPinState {
    Idle,
    Loading,
    Success,
    Error,
}

internal data class VaultBackupState(
    val verifyPinState: VerifyPinState = VerifyPinState.Idle,
    val sentEmailTo: String,
    val isSingleKeygen: Boolean = false,
)

private const val FAST_VAULT_VERIFICATION_SUCCESS_DELAY = 400L

@HiltViewModel
internal class FastVaultVerificationViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    private val saveVault: SaveVaultUseCase,
    private val verifyFastVaultBackupCode: VerifyFastVaultBackupCodeUseCase,
    private val temporaryVaultRepository: TemporaryVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val vaultMetadataRepo: VaultMetadataRepo,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.FastVaultVerification>()
    private val email = args.email
    private val isSingleKeygen = args.tssAction == TssAction.SingleKeygen

    val state =
        MutableStateFlow(VaultBackupState(sentEmailTo = email, isSingleKeygen = isSingleKeygen))

    val codeFieldState = TextFieldState()

    fun processCode(code: String) {
        if (code.length >= FAST_VAULT_VERIFICATION_CODE_LENGTH) {
            verify()
        } else {
            updateVerifyState(VerifyPinState.Idle)
        }
    }

    fun paste(code: String) {
        codeFieldState.setTextAndPlaceCursorAtEnd(code)
        updateVerifyState(VerifyPinState.Idle)
    }

    fun back() {
        if (isSingleKeygen) return // Non-dismissable: user must verify OTP
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun changeEmail() {
        if (isSingleKeygen) return // Cannot change email for MLDSA keygen on existing vault
        viewModelScope.launch {
            navigator.route(
                route = Route.EnterVaultInfo(count = 1, tssAction = args.tssAction),
                opts =
                    NavigationOptions(popUpToRoute = Route.EnterVaultInfo::class, inclusive = true),
            )
        }
    }

    private fun verify() {
        if (state.value.verifyPinState == VerifyPinState.Loading) return
        viewModelScope.launch {
            val code = codeFieldState.text.toString()

            if (isCodeTemplateValid(code)) {
                updateVerifyState(VerifyPinState.Loading)

                val isCodeValid =
                    verifyFastVaultBackupCode(publicKeyEcdsa = args.pubKeyEcdsa, code = code)
                if (isCodeValid) {
                    try {
                        updateVerifyState(VerifyPinState.Success)

                        val vaultId = args.vaultId

                        if (isSingleKeygen) {
                            // MLDSA keygen on existing fast vault: merge MLDSA
                            // data into existing vault and auto-enable QBTC
                            val tempVault = temporaryVaultRepository.getById(vaultId)
                            val existingVault =
                                vaultRepository.get(vaultId)
                                    ?: error(
                                        "No vault with id $vaultId exists for SingleKeygen save"
                                    )
                            existingVault.pubKeyMLDSA = tempVault.vault.pubKeyMLDSA
                            existingVault.keyshares = tempVault.vault.keyshares
                            saveVault(existingVault, true)

                            val qbtcToken = Coins.Qbtc.QBTC
                            val (address, pubKey) =
                                chainAccountAddressRepository.getAddress(qbtcToken, existingVault)
                            vaultRepository.addTokenToVault(
                                vaultId,
                                qbtcToken.copy(address = address, hexPublicKey = pubKey),
                            )

                            delay(FAST_VAULT_VERIFICATION_SUCCESS_DELAY)
                            navigator.route(route = Route.Home(openVaultId = vaultId))
                        } else {
                            val vault = temporaryVaultRepository.getById(vaultId)
                            val shouldOverride = args.tssAction == TssAction.Migrate
                            saveVault(vault.vault, shouldOverride)

                            vault.hint?.let {
                                vaultDataStoreRepository.setFastSignHint(
                                    vaultId = vaultId,
                                    hint = it,
                                )
                            }

                            if (context.canAuthenticateBiometric()) {
                                vaultPasswordRepository.savePassword(vaultId, vault.password)
                            }

                            vaultMetadataRepo.setFastVaultPasswordReminderShownDate(
                                vaultId = vaultId,
                                date = Clock.System.todayIn(TimeZone.currentSystemDefault()),
                            )

                            delay(FAST_VAULT_VERIFICATION_SUCCESS_DELAY)
                            navigator.route(
                                route =
                                    Route.BackupVault(
                                        vaultId = args.vaultId,
                                        vaultType = VaultType.Fast,
                                        action = args.tssAction,
                                        passwordType =
                                            BackupPasswordType.VultiServerPassword(
                                                password = args.password
                                            ),
                                    )
                            )
                        }
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.e(e, "FastVaultVerification: save vault failed")
                        updateVerifyState(VerifyPinState.Error)
                    }
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
        state.update { it.copy(verifyPinState = verifyState) }
    }

    companion object {
        const val FAST_VAULT_VERIFICATION_CODE_LENGTH = 4
    }
}
