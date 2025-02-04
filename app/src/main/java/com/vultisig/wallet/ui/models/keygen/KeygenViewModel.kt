@file:OptIn(ExperimentalUuidApi::class, ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.content.Context
import android.content.Intent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import com.vultisig.wallet.data.tss.LocalStateAccessorImpl
import com.vultisig.wallet.data.tss.TssMessagePuller
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
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
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal data class KeygenUiModel(
    val progress: Float = 0f,
    val isSuccess: Boolean = false,
    val steps: List<KeygenStepUiModel> = emptyList(),
)

internal data class KeygenStepUiModel(
    val title: UiText,
    val isLoading: Boolean,
)

@HiltViewModel
internal class KeygenViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,

    @ApplicationContext private val context: Context,
    private val saveVault: SaveVaultUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
) : ViewModel() {

    val state = MutableStateFlow(KeygenUiModel())

    private val params = savedStateHandle.toRoute<Route.Keygen.Generating>()

    private val vault = Vault(
        id = Uuid.random().toHexString(),
        name = params.name,
        hexChainCode = params.hexChainCode,
        localPartyID = params.localPartyId,
        signers = params.keygenCommittee,
        // todo check if value correct or if we can get it here
        resharePrefix = params.oldResharePrefix,
        libType = params.libType,
    )

    private val action: TssAction = params.action
    private val keygenCommittee: List<String> = params.keygenCommittee + vault.localPartyID
    private val oldCommittee: List<String> = params.oldCommittee
    private val serverUrl: String = params.serverUrl
    private val sessionId: String = params.sessionId
    private val encryptionKeyHex: String = params.encryptionKeyHex
    private val oldResharePrefix: String = params.oldResharePrefix
    private val isInitiatingDevice: Boolean = params.isInitiatingDevice
    private val libType = params.libType

    private val keyshares = mutableListOf<KeyShare>()

    private val localStateAccessor: tss.LocalStateAccessor = LocalStateAccessorImpl(keyshares)
    private var featureFlag: FeatureFlagJson? = null

    // TODO check if this is required when we add reshare
    private val isReshareMode: Boolean = false

    private val dklsKeygen = DKLSKeygen(
        localPartyId = vault.localPartyID,
        keygenCommittee = keygenCommittee,
        mediatorURL = serverUrl,
        sessionID = sessionId,
        encryptionKeyHex = encryptionKeyHex,
        isInitiateDevice = isInitiatingDevice,
        encryption = encryption,
        sessionApi = sessionApi,
    )

    init {
        generateKey()
    }

    private suspend fun startKeygen() {
        try {
            sessionApi.startWithCommittee(
                serverUrl, sessionId, keygenCommittee
            )
            Timber.d("startKeygen: Keygen started")

        } catch (e: Exception) {
            Timber.e(e, "startKeygen")
        }
    }

    private fun generateKey() {
        viewModelScope.launch {
            updateStep(KeygenState.CreatingInstance)

            startKeygen()

            try {
                when (libType) {
                    SigningLibType.DKLS -> startKeygenDkls()
                    SigningLibType.GG20 -> startKeygenGG20()
                }

                updateStep(KeygenState.Success)

                delay(1.seconds)

                saveVault()
            } catch (e: Exception) {
                Timber.d(e, "generateKey error")

                // TODO handle keygen error
                // state.value = resolveKeygenErrorFromException(e)

                stopService()
            }
        }
    }

    private suspend fun startKeygenDkls() {

        updateStep(KeygenState.KeygenECDSA)

        dklsKeygen.dklsKeygenWithRetry(0)

        updateStep(KeygenState.KeygenEdDSA)

        val schnorr = SchnorrKeygen(
            localPartyId = vault.localPartyID,
            keygenCommittee = keygenCommittee,
            mediatorURL = serverUrl,
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
            serverUrl,
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
                serverUrl,
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
                        updateStep(KeygenState.KeygenECDSA)
                        val keygenRequest = tss.KeygenRequest()
                        keygenRequest.localPartyID = vault.localPartyID
                        keygenRequest.allParties = keygenCommittee.joinToString(",")
                        keygenRequest.chainCodeHex = vault.hexChainCode
                        val ecdsaResp = service.keygenECDSA(keygenRequest)
                        vault.pubKeyECDSA = ecdsaResp.pubKey
                        delay(1.seconds) // backoff for 1 second
                        updateStep(KeygenState.KeygenEdDSA)
                        val eddsaResp = service.keygenEdDSA(keygenRequest)
                        vault.pubKeyEDDSA = eddsaResp.pubKey
                    }

                    TssAction.ReShare -> {
                        updateStep(KeygenState.ReshareECDSA)
                        val reshareRequest = tss.ReshareRequest()
                        reshareRequest.localPartyID = vault.localPartyID
                        reshareRequest.pubKey = vault.pubKeyECDSA
                        reshareRequest.oldParties = oldCommittee.joinToString(",")
                        reshareRequest.newParties = keygenCommittee.joinToString(",")
                        reshareRequest.resharePrefix =
                            vault.resharePrefix.ifEmpty { oldResharePrefix }
                        reshareRequest.chainCodeHex = vault.hexChainCode
                        val ecdsaResp = service.reshareECDSA(reshareRequest)
                        updateStep(KeygenState.ReshareEdDSA)
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
                    serverUrl,
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
                sessionApi.getCompletedParties(serverUrl, sessionId)
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
            serverUrl,
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

    // TODO consider saving vault only on backup screens after backups verified
    private suspend fun saveVault() {
        val vaultId = Uuid.random().toHexString()

        saveVault(
            this.vault,
            this.action == TssAction.ReShare
        )
        vaultDataStoreRepository.setBackupStatus(vaultId = vaultId, false)
        // hint?.let { vaultDataStoreRepository.setFastSignHint(vaultId = vaultId, hint = it) }
        delay(2.seconds)

        stopService()

        lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)

        // if (password?.isNotEmpty() == true && context.canAuthenticateBiometric()) {
        //     vaultPasswordRepository.savePassword(vaultId, password)
        // }

        navigator.navigate(
            dst = Destination.BackupSuggestion(
                vaultId = vaultId
            ),
            opts = NavigationOptions(
                popUpTo = Destination.Home().route,
            )
        )
    }

    private fun stopService() {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stop MediatorService: Mediator service stopped")
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


    private fun updateStep(step: KeygenState) {
        state.update {
            it.copy(
                isSuccess = step is KeygenState.Success,
                progress = when (step) {
                    is KeygenState.CreatingInstance -> 0.25f
                    is KeygenState.KeygenECDSA -> 0.50f
                    is KeygenState.KeygenEdDSA -> 0.75f
                    is KeygenState.ReshareECDSA -> 0.5f
                    is KeygenState.ReshareEdDSA -> 0.75f
                    is KeygenState.Success -> 1f

                    else -> 0.75f // TODO remove VerifyBackup state when it's unusable
                },
                steps = it.steps.map { it.copy(isLoading = false) } + listOfNotNull(
                    when (step) {
                        is KeygenState.CreatingInstance -> KeygenStepUiModel(
                            UiText.StringResource(R.string.keygen_step_preparing_vault),
                            true
                        )

                        is KeygenState.KeygenECDSA -> KeygenStepUiModel(
                            UiText.StringResource(R.string.keygen_step_generating_ecdsa),
                            true
                        )

                        is KeygenState.KeygenEdDSA -> KeygenStepUiModel(
                            UiText.StringResource(R.string.keygen_step_generating_eddsa),
                            true
                        )

                        else -> null
                    }
                )
            )
        }
    }

}

private const val MAX_KEYGEN_ATTEMPTS = 3