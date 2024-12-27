package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.keygen.DKLSKeygen
import com.vultisig.wallet.data.keygen.SchnorrKeygen
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.models.KeyShare
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessagePuller
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.DuplicateVaultException
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.ui.components.canAuthenticateBiometric
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.ServiceImpl
import tss.Tss
import kotlin.time.Duration.Companion.seconds

internal sealed interface KeygenState {
    data object CreatingInstance : KeygenState
    data object KeygenECDSA : KeygenState
    data object KeygenEdDSA : KeygenState
    data object ReshareECDSA : KeygenState
    data object ReshareEdDSA : KeygenState
    data object Success : KeygenState
    data object VerifyBackup : KeygenState

    data class Error(
        val title: UiText?,
        val message: UiText,
    ) : KeygenState
}

internal class GeneratingKeyViewModel(
    private val vault: Vault,
    private val action: TssAction,
    private val keygenCommittee: List<String>,
    private val oldCommittee: List<String>,
    private val serverAddress: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
    private val oldResharePrefix: String,
    private val password: String? = null,
    private val hint: String? = null,
    private val vaultSetupType: VaultSetupType?,
    private val isInitiatingDevice: Boolean,

    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    private val saveVault: SaveVaultUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    internal val isReshareMode: Boolean,
    private val featureFlagApi: FeatureFlagApi,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val vaultMetadataRepo: VaultMetadataRepo,
    private val vultiSignerRepository: VultiSignerRepository,
) : ViewModel() {

    val state = MutableStateFlow<KeygenState>(KeygenState.CreatingInstance)

    private val localStateAccessor: tss.LocalStateAccessor = LocalStateAccessor(vault)
    private var featureFlag: FeatureFlagJson? = null

    private val dklsKeygen = DKLSKeygen(
        vault = vault,
        keygenCommittee = keygenCommittee,
        mediatorURL = serverAddress,
        sessionID = sessionId,
        encryptionKeyHex = encryptionKeyHex,
        isInitiateDevice = isInitiatingDevice,
        encryption = encryption,
        sessionApi = sessionApi,
    )


    suspend fun generateKey() {
        state.value = KeygenState.CreatingInstance

        try {
            when (vault.libType) {
                SigningLibType.DKLS -> startKeygenDkls()
                SigningLibType.GG20 -> startKeygenGG20()
            }

            vault.signers = keygenCommittee
            state.value = KeygenState.Success

            if (password != null && vaultSetupType == VaultSetupType.FAST) {
                delay(2.seconds)

                state.value = KeygenState.VerifyBackup
            } else {
                saveVault()
            }
        } catch (e: Exception) {
            Timber.d("generateKey error: %s", e.stackTraceToString())

            state.value = resolveKeygenErrorFromException(e)

            stopService()
        }
    }

    private suspend fun startKeygenDkls() {

        state.value = KeygenState.KeygenECDSA

        dklsKeygen.dklsKeygenWithRetry(0)

        state.value = KeygenState.KeygenEdDSA

        val schnorr = SchnorrKeygen(
            vault = vault,
            keygenCommittee = keygenCommittee,
            mediatorURL = serverAddress,
            sessionID = sessionId,
            encryptionKeyHex = encryptionKeyHex,

            encryption = encryption,
            sessionApi = sessionApi,
            setupMessage = dklsKeygen.setupMessage,
        )

        schnorr.schnorrKeygenWithRetry(0)

        val keyshareEcdsa = dklsKeygen.keyshare!!
        val keyshareEddsa = schnorr.keyshare!!

        vault.pubKeyECDSA = keyshareEcdsa.pubKey
        vault.pubKeyEDDSA = keyshareEddsa.pubKey
        vault.hexChainCode = keyshareEcdsa.chaincode
        vault.keyshares = listOf(
            KeyShare(
                pubKey = keyshareEcdsa.pubKey,
                keyShare = keyshareEcdsa.keyshare
            ),
            KeyShare(
                pubKey = keyshareEddsa.pubKey,
                keyShare = keyshareEddsa.keyshare
            )
        )

        sessionApi.markLocalPartyComplete(
            serverAddress,
            sessionId,
            listOf(vault.localPartyID)
        )

        waitCompleteParties()
    }

    private suspend fun startKeygenGG20() {
        featureFlag = featureFlagApi.getFeatureFlag()

        val tss = createTss()

        keygenWithRetry(tss, 1)
    }

    private suspend fun keygenWithRetry(service: ServiceImpl, attempt: Int = 1) {
        withContext(Dispatchers.IO) {
            val messagePuller = TssMessagePuller(
                service,
                encryptionKeyHex,
                serverAddress,
                vault.localPartyID,
                sessionId,
                sessionApi,
                encryption,
                featureFlag?.isEncryptGcmEnabled == true
            )

            try {
                messagePuller.pullMessages(null)

                when (action) {
                    TssAction.KEYGEN -> {
                        // generate ECDSA
                        state.value = KeygenState.KeygenECDSA
                        val keygenRequest = tss.KeygenRequest()
                        keygenRequest.localPartyID = vault.localPartyID
                        keygenRequest.allParties = keygenCommittee.joinToString(",")
                        keygenRequest.chainCodeHex = vault.hexChainCode
                        val ecdsaResp = service.keygenECDSA(keygenRequest)
                        vault.pubKeyECDSA = ecdsaResp.pubKey
                        delay(1.seconds) // backoff for 1 second
                        state.value = KeygenState.KeygenEdDSA
                        val eddsaResp = service.keygenEdDSA(keygenRequest)
                        vault.pubKeyEDDSA = eddsaResp.pubKey
                    }

                    TssAction.ReShare -> {
                        state.value = KeygenState.ReshareECDSA
                        val reshareRequest = tss.ReshareRequest()
                        reshareRequest.localPartyID = vault.localPartyID
                        reshareRequest.pubKey = vault.pubKeyECDSA
                        reshareRequest.oldParties = oldCommittee.joinToString(",")
                        reshareRequest.newParties = keygenCommittee.joinToString(",")
                        reshareRequest.resharePrefix =
                            vault.resharePrefix.ifEmpty { oldResharePrefix }
                        reshareRequest.chainCodeHex = vault.hexChainCode
                        val ecdsaResp = service.reshareECDSA(reshareRequest)
                        state.value = KeygenState.ReshareEdDSA
                        delay(1.seconds) // backoff for 1 second
                        reshareRequest.pubKey = vault.pubKeyEDDSA
                        reshareRequest.newResharePrefix = ecdsaResp.resharePrefix
                        val eddsaResp = service.resharingEdDSA(reshareRequest)
                        vault.pubKeyEDDSA = eddsaResp.pubKey
                        vault.pubKeyECDSA = ecdsaResp.pubKey
                        vault.resharePrefix = ecdsaResp.resharePrefix
                    }
                }

                // here is the keygen process is done
                sessionApi.markLocalPartyComplete(
                    serverAddress,
                    sessionId,
                    listOf(vault.localPartyID)
                )
                Timber.d("Local party ${vault.localPartyID} marked as complete")

                waitCompleteParties()

                Timber.d("All parties have completed the key generation process")

                messagePuller.stop()
            } catch (e: Exception) {
                messagePuller.stop()

                Timber.e(e, "attempt $attempt keygenWithRetry failed")

                if (attempt < MAX_KEYGEN_ATTEMPTS) {
                    keygenWithRetry(service, attempt + 1)
                } else {
                    throw e
                }
            }
        }
    }

    private suspend fun waitCompleteParties() {
        var counter = 0
        var isSuccess = false
        while (counter < 60) {
            val serverCompletedParties =
                sessionApi.getCompletedParties(serverAddress, sessionId)
            if (serverCompletedParties.containsAll(keygenCommittee)) {
                isSuccess = true
                break // this means all parties have completed the key generation process
            }
            delay(1.seconds)
            counter++
        }

        if (!isSuccess) {
            throw Exception("Timeout waiting for all parties to complete the key generation process")
        }
    }

    private suspend fun createTss(): ServiceImpl = withContext(Dispatchers.IO) {
        val messenger = TssMessenger(
            serverAddress,
            sessionId,
            encryptionKeyHex,
            sessionApi = sessionApi,
            coroutineScope = viewModelScope,
            encryption = encryption,
            isEncryptionGCM = featureFlag?.isEncryptGcmEnabled == true,
        )

        // this will take a while
        return@withContext Tss.newService(messenger, localStateAccessor, true)
    }

    private suspend fun saveVault() {
        val vaultId = vault.id

        saveVault(
            this@GeneratingKeyViewModel.vault,
            this@GeneratingKeyViewModel.action == TssAction.ReShare
        )
        vaultDataStoreRepository.setBackupStatus(vaultId = vaultId, false)
        hint?.let { vaultDataStoreRepository.setFastSignHint(vaultId = vaultId, hint = it) }
        delay(2.seconds)

        stopService()

        lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)

        if (password?.isNotEmpty() == true && context.canAuthenticateBiometric()) {
            vaultPasswordRepository.savePassword(vaultId, password)
        }

        navigator.navigate(
            dst = Destination.BackupSuggestion(
                vaultId = vaultId
            ),
            opts = NavigationOptions(
                popUpTo = Destination.Home().route,
            )
        )
    }

    fun stopService() {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stop MediatorService: Mediator service stopped")

    }

    val verifyState = MutableStateFlow(KeygenVerifyServerBackupUiModel())
    val codeFieldState = TextFieldState()

    fun completeVerification() {
        val code = codeFieldState.text.toString()

        viewModelScope.launch {
            setVerifyError(null)

            val isCodeValid = vultiSignerRepository.isBackupCodeValid(
                publicKeyEcdsa = vault.pubKeyECDSA,
                code = code,
            )

            if (isCodeValid) {
                try {
                    saveVault()
                } catch (e: DuplicateVaultException) {
                    setVerifyError(UiText.StringResource(R.string.import_file_screen_duplicate_vault))
                }
            } else {
                setVerifyError(UiText.StringResource(R.string.keygen_verify_server_backup_invalid_code))
            }
        }
    }

    private fun setVerifyError(error: UiText?) {
        verifyState.update {
            it.copy(
                codeError = error,
            )
        }
    }

    private fun resolveKeygenErrorFromException(e: Exception): KeygenState.Error {
        val isThresholdError = checkIsThresholdError(e)

        return KeygenState.Error(
            title = when {
                isThresholdError -> null
                isReshareMode -> UiText.StringResource(R.string.generating_key_screen_reshare_failed)
                else -> UiText.StringResource(R.string.generating_key_screen_keygen_failed)
            },
            message = if (isThresholdError) {
                UiText.StringResource(R.string.threshold_error)
            } else {
                UiText.DynamicString(e.message ?: "Unknown error")
            }
        )
    }

    private fun checkIsThresholdError(exception: Exception) =
        exception.message?.let { message ->
            message.contains("threshold") ||
                    message.contains("failed to update from bytes to new local party")
        } ?: false
}

private const val MAX_KEYGEN_ATTEMPTS = 3