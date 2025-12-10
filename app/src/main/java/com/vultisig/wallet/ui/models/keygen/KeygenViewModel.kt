@file:OptIn(ExperimentalUuidApi::class)

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
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.vault.TempVaultDto
import com.vultisig.wallet.data.repositories.vault.TemporaryVaultRepository
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessagePuller
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.ui.components.canAuthenticateBiometric
import com.vultisig.wallet.ui.components.errors.ErrorUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.BackupVault.BackupPasswordType
import com.vultisig.wallet.ui.navigation.Route.VaultInfo.VaultType
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

internal sealed interface KeygenState {
    data object CreatingInstance : KeygenState
    data object KeygenECDSA : KeygenState
    data object KeygenEdDSA : KeygenState
    data object ReshareECDSA : KeygenState
    data object ReshareEdDSA : KeygenState
    data object Success : KeygenState

    data class Error(
        val title: UiText?,
        val message: UiText,
    ) : KeygenState
}


internal data class KeygenUiModel(
    val progress: Float = 0f,
    val isSuccess: Boolean = false,
    val steps: List<KeygenStepUiModel> = emptyList(),
    val error: ErrorUiModel? = null,
    val action: TssAction = TssAction.KEYGEN,
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
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val temporaryVaultRepository: TemporaryVaultRepository,
    private val vaultRepository: VaultRepository,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.Keygen.Generating>()

    val state = MutableStateFlow(
        KeygenUiModel(
            action = args.action
        )
    )

    private val vault = Vault(
        id = args.vaultId ?: Uuid.random().toHexString(),
        name = args.vaultName,
        hexChainCode = args.hexChainCode,
        localPartyID = args.localPartyId,
        signers = args.keygenCommittee,
        resharePrefix = args.oldResharePrefix,
        libType = args.libType,
    )

    private val action: TssAction = args.action
    private val keygenCommittee: List<String> = args.keygenCommittee
    private val oldCommittee: List<String> = args.oldCommittee
    private val serverUrl: String = args.serverUrl
    private val sessionId: String = args.sessionId
    private val encryptionKeyHex: String = args.encryptionKeyHex
    private val oldResharePrefix: String = args.oldResharePrefix
    private val isInitiatingDevice: Boolean = args.isInitiatingDevice
    private val libType = args.libType

    private val localStateAccessor: tss.LocalStateAccessor = LocalStateAccessor(vault)
    private var featureFlag: FeatureFlagJson? = null

    private val isReshareMode: Boolean = action == TssAction.ReShare

    init {
        generateKey()
    }

    fun tryAgain() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    private fun generateKey() {
        viewModelScope.launch {
            updateStep(KeygenState.CreatingInstance)

            args.vaultId?.let { vaultId ->
                val cachedVault = vaultRepository.get(vaultId)
                if (cachedVault != null) {
                    vault.pubKeyECDSA = cachedVault.pubKeyECDSA
                    vault.pubKeyEDDSA = cachedVault.pubKeyEDDSA
                    vault.keyshares = cachedVault.keyshares
                }
            }

            state.update { it.copy(error = null) }

            try {
                if (isInitiatingDevice) {
                    startKeygen()
                }

                when (libType) {
                    SigningLibType.DKLS -> startKeygenDkls()
                    SigningLibType.GG20 -> startKeygenGG20()
                }

                updateStep(KeygenState.Success)

                delay(1.seconds)

                saveVault()
            } catch (e: Exception) {
                Timber.d(e, "generateKey error")

                state.update {
                    it.copy(
                        error = resolveKeygenErrorFromException(e)
                    )
                }

                stopService()
            }
        }
    }

    private suspend fun startKeygenDkls() {

        updateStep(KeygenState.KeygenECDSA)

        var localUiEcdsa = ""
        var localUiEddsa = ""

        if (action == TssAction.Migrate) {
            try {
                // Verify both key shares exist before attempting migration
                val ecdsaShare = vault.getKeyshare(vault.pubKeyECDSA)
                val eddsaShare = vault.getKeyshare(vault.pubKeyEDDSA)

                if (ecdsaShare.isNullOrBlank() || eddsaShare.isNullOrBlank()) {
                    throw RuntimeException("Missing key shares required for migration")
                }

                val ecdsaUIResp = Tss.getLocalUIEcdsa(ecdsaShare)
                localUiEcdsa = ecdsaUIResp.padEnd(64, '0')

                val eddsaUIResp = Tss.getLocalUIEddsa(eddsaShare)
                localUiEddsa = eddsaUIResp.padEnd(64, '0')

            } catch (e: Exception) {
                error("Can't get local ui for migration")
            }
        }

        val dklsKeygen = DKLSKeygen(
            localPartyId = vault.localPartyID,
            keygenCommittee = keygenCommittee,
            mediatorURL = serverUrl,
            sessionID = sessionId,
            encryptionKeyHex = encryptionKeyHex,
            isInitiateDevice = isInitiatingDevice,
            encryption = encryption,
            sessionApi = sessionApi,
            hexChainCode = vault.hexChainCode,
            localUi = localUiEcdsa,

            action = action,
            oldCommittee = oldCommittee,
            vault = vault,
        )

        when (action) {
            TssAction.KEYGEN, TssAction.Migrate -> dklsKeygen.dklsKeygenWithRetry(0)
            TssAction.ReShare -> dklsKeygen.reshareWithRetry(0)
            TssAction.KeyImport-> error("KeyImport not supported yet")
        }

        updateStep(KeygenState.KeygenEdDSA)

        val schnorr = SchnorrKeygen(
            localPartyId = vault.localPartyID,
            keygenCommittee = keygenCommittee,
            vault = vault,
            oldCommittee = oldCommittee,
            mediatorURL = serverUrl,
            sessionID = sessionId,
            encryptionKeyHex = encryptionKeyHex,
            action = action,

            encryption = encryption,
            sessionApi = sessionApi,
            setupMessage = dklsKeygen.setupMessage,
            isInitiatingDevice = isInitiatingDevice,
            hexChainCode = vault.hexChainCode,
            localUi = localUiEddsa
        )

        when (action) {
            TssAction.KEYGEN, TssAction.Migrate -> schnorr.schnorrKeygenWithRetry(0)
            TssAction.ReShare -> schnorr.schnorrReshareWithRetry(0)
            TssAction.KeyImport-> error("KeyImport not supported yet")
        }

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

        if (action == TssAction.Migrate) {
            vault.libType = SigningLibType.DKLS
        }

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
                    TssAction.KEYGEN, TssAction.Migrate -> {
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
                    TssAction.KeyImport-> error("KeyImport will not support for GG20")
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

    private suspend fun saveVault() {
        val vaultId = vault.id

        val password = args.password
        if (!args.email.isNullOrBlank() && !password.isNullOrBlank()) {
            temporaryVaultRepository.add(
                TempVaultDto(
                    vault = vault,
                    email = args.email,
                    password = password,
                    hint = args.hint,
                )
            )

            if (context.canAuthenticateBiometric()) {
                vaultPasswordRepository.savePassword(vaultId, password)
            }
        } else {
            val shouldOverrideVault = isReshareMode || action == TssAction.Migrate

            saveVault(vault, shouldOverrideVault)

            vaultDataStoreRepository.setBackupStatus(vaultId = vaultId, false)
        }

        lastOpenedVaultRepository.setLastOpenedVaultId(vaultId)

        delay(2.seconds)

        stopService()

        val vaultType =
            if (vault.isFastVault() && !args.email.isNullOrEmpty())
                VaultType.Fast
            else VaultType.Secure

        navigator.route(
            route = when (action) {
                TssAction.KEYGEN, TssAction.ReShare ->
                    Route.Onboarding.VaultBackup(
                        vaultId = vaultId,
                        pubKeyEcdsa = vault.pubKeyECDSA,
                        email = args.email,
                        vaultType = vaultType,
                        action = action,
                        vaultName = args.vaultName,
                        password = args.password,
                    )

                TssAction.Migrate, TssAction.KeyImport -> if (vault.isFastVault()) {
                    Route.Onboarding.VaultBackup(
                        vaultId = vaultId,
                        pubKeyEcdsa = vault.pubKeyECDSA,
                        email = args.email,
                        vaultType = vaultType,
                        action = action,
                        vaultName = args.vaultName,
                        password = args.password,
                    )
                } else {
                    Route.BackupVault(
                        vaultId = vaultId,
                        vaultType = vaultType,
                        action = args.action,
                        passwordType = BackupPasswordType.UserSelectionPassword
                    )
                }
            },
            opts = NavigationOptions(
                popUpToRoute = Route.Keygen.Generating::class,
                inclusive = true,
            ),
        )
    }

    private fun stopService() {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stop MediatorService: Mediator service stopped")
    }

    private fun resolveKeygenErrorFromException(e: Exception): ErrorUiModel {
        val isThresholdError = checkIsThresholdError(e)

        return ErrorUiModel(
            title = when {
                isReshareMode -> UiText.StringResource(R.string.generating_key_screen_reshare_failed)
                else -> UiText.StringResource(R.string.generating_key_screen_keygen_failed)
            },
            description = if (isThresholdError) {
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

                    else -> 0.75f
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

                        is KeygenState.ReshareECDSA -> KeygenStepUiModel(
                            UiText.StringResource(R.string.reshare_step_generating_ecdsa),
                            true
                        )

                        is KeygenState.ReshareEdDSA -> KeygenStepUiModel(
                            UiText.StringResource(R.string.reshare_step_generating_eddsa),
                            true
                        )

                        else -> null
                    }
                )
            )
        }
    }

    // TODO peer discovery might be a better place for that method
    private suspend fun startKeygen() {
        sessionApi.startWithCommittee(
            serverUrl, sessionId, keygenCommittee
        )
    }
    override fun onCleared() {
        stopService()
    }
}

private const val MAX_KEYGEN_ATTEMPTS = 3