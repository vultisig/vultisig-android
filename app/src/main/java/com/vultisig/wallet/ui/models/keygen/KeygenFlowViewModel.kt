@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinReshareRequestJson
import com.vultisig.wallet.data.common.Endpoints.LOCAL_MEDIATOR_SERVER_URL
import com.vultisig.wallet.data.common.Endpoints.VULTISIG_RELAY_URL
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import com.vultisig.wallet.data.models.proto.v1.toProto
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.SecretSettingsRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.GenerateServerPartyId
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.MakeQrCodeBitmapShareFormat
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_SETUP_TYPE
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.NetworkPromptOption
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.encodeBase64
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject

enum class KeygenFlowState {
    PEER_DISCOVERY, DEVICE_CONFIRMATION, KEYGEN,
}

internal data class KeygenFlowUiModel(
    val currentState: KeygenFlowState = KeygenFlowState.PEER_DISCOVERY,
    val isReshareMode: Boolean = false,
    val selection: List<String> = emptyList(),
    val deletedParticipants: List<String> = emptyList(),
    val participants: List<String> = emptyList(),
    val qrBitmapPainter: BitmapPainter? = null,
    val networkOption: NetworkPromptOption = NetworkPromptOption.INTERNET,
    val vaultSetupType: VaultSetupType = VaultSetupType.SECURE,
    val isLoading: Boolean = false,
) {
    val isContinueButtonEnabled =
        if (isReshareMode) {
            selection.size >= 2
        } else {
            when (vaultSetupType) {
                VaultSetupType.FAST -> {
                    selection.size == 2
                }

                VaultSetupType.ACTIVE -> {
                    selection.size == 3
                }

                VaultSetupType.SECURE -> {
                    selection.size >= 2
                }
            }
        }
}

@HiltViewModel
internal class KeygenFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    @ApplicationContext private val context: Context,

    private val compressQr: CompressQrUseCase,
    private val saveVault: SaveVaultUseCase,
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat,
    private val generateQrBitmap: GenerateQrBitmap,
    private val encryption: Encryption,
    private val generateServerPartyId: GenerateServerPartyId,
    private val generateServiceName: GenerateServiceName,
    private val discoverParticipants: DiscoverParticipantsUseCase,

    private val vaultRepository: VaultRepository,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val signerRepository: VultiSignerRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val vaultMetadataRepo: VaultMetadataRepo,
    private val secretSettingsRepository: SecretSettingsRepository,

    private val protoBuf: ProtoBuf,
    private val sessionApi: SessionApi,
    private val featureFlagApi: FeatureFlagApi,
) : ViewModel() {

    private val setupType = VaultSetupType.fromInt(
        savedStateHandle.get<Int>(ARG_VAULT_SETUP_TYPE) ?: 0
    )

    private val vaultId: String? = savedStateHandle[Destination.KeygenFlow.ARG_VAULT_ID]
    private val vaultName: String? = savedStateHandle[Destination.KeygenFlow.ARG_VAULT_NAME]
    private val email: String? = savedStateHandle[Destination.ARG_EMAIL]
    private val password: String? = savedStateHandle[Destination.ARG_PASSWORD]
    private val passwordHint: String? = savedStateHandle[Destination.KeygenFlow.ARG_PASSWORD_HINT]


    val uiState = MutableStateFlow(
        KeygenFlowUiModel(
            vaultSetupType = setupType,
        )
    )

    private var serverUrl: String = VULTISIG_RELAY_URL
    private val serviceName: String = generateServiceName()
    private val sessionID: String = UUID.randomUUID().toString()

    private val action = MutableStateFlow(TssAction.KEYGEN)
    private var vault: Vault = Vault(id = UUID.randomUUID().toString(), "")
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex
    private var _oldResharePrefix: String = ""

    private val shareQrBitmap = MutableStateFlow<Bitmap?>(null)

    private val isFastSign: Boolean =
        setupType.isFast && email != null && password != null

    val localPartyID: String
        get() = vault.localPartyID

    val generatingKeyViewModel: GeneratingKeyViewModel
        get() = GeneratingKeyViewModel(
            vault = vault,
            action = this.action.value,
            keygenCommittee = uiState.value.selection,
            oldCommittee = vault.signers.filter { uiState.value.selection.contains(it) },
            serverAddress = serverUrl,
            sessionId = sessionID,
            encryptionKeyHex = _encryptionKeyHex,
            oldResharePrefix = _oldResharePrefix,
            password = password,
            hint = passwordHint,
            vaultSetupType = setupType,
            isReshareMode = uiState.value.isReshareMode,
            isInitiatingDevice = true,

            navigator = navigator,
            saveVault = saveVault,
            lastOpenedVaultRepository = lastOpenedVaultRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
            context = context,
            sessionApi = sessionApi,
            encryption = encryption,
            featureFlagApi = featureFlagApi,
            vaultPasswordRepository = vaultPasswordRepository,
            vaultMetadataRepo = vaultMetadataRepo,
            vultiSignerRepository = signerRepository,
        )


    private var discoverParticipantsJob: Job? = null

    init {
        viewModelScope.launch {
            setData(vaultId)
        }

        viewModelScope.launch {
            if (setupType == VaultSetupType.FAST) {
                uiState.map { it.selection }
                    .cancellable()
                    .collect {
                        if (it.size == VaultSetupType.FAST_PARTICIPANTS_KICKOFF_THRESHOLD) {
                            finishPeerDiscovery()
                            cancel()
                        }
                    }
            }
        }
    }

    private suspend fun setData(vaultId: String?) {
        vault = if (vaultId == null) {
            // generate
            val vaultName = vaultName ?: error("No vault name provided")

            uiState.update { it.copy(isReshareMode = false) }
            action.value = TssAction.KEYGEN

            Vault(
                id = UUID.randomUUID().toString(),
                name = vaultName,
                hexChainCode = Utils.encryptionKeyHex,
                localPartyID = Utils.deviceName(context),
            )
        } else {
            // reshare
            val vault = vaultRepository.get(vaultId) ?: error("No vault with id $vaultId")

            uiState.update { it.copy(isReshareMode = true) }
            action.value = TssAction.ReShare

            vault
        }

        vault.libType = if (secretSettingsRepository.isDklsEnabled.first()) {
            SigningLibType.DKLS
        } else {
            SigningLibType.GG20
        }

        serverUrl = VULTISIG_RELAY_URL
        uiState.update { it.copy(selection = listOf(vault.localPartyID)) }
        _oldResharePrefix = vault.resharePrefix

        updateKeygenPayload()
    }

    private suspend fun updateKeygenPayload() {
        stopParticipantDiscovery()

        val isRelayEnabled =
            uiState.value.networkOption == NetworkPromptOption.INTERNET || isFastSign

        val keygenPayload = when (action.value) {
            TssAction.KEYGEN -> {
                "https://vultisig.com?type=NewVault&tssType=Keygen&jsonData=" +
                        compressQr(
                            protoBuf.encodeToByteArray(
                                KeygenMessageProto(
                                    sessionId = sessionID,
                                    hexChainCode = vault.hexChainCode,
                                    serviceName = serviceName,
                                    encryptionKeyHex = this._encryptionKeyHex,
                                    useVultisigRelay = isRelayEnabled,
                                    vaultName = this.vault.name,
                                    libType = this.vault.libType.toProto(),
                                )
                            )
                        ).encodeBase64()
            }

            TssAction.ReShare -> {
                "https://vultisig.com?type=NewVault&tssType=Reshare&jsonData=" +
                        compressQr(
                            protoBuf.encodeToByteArray(
                                ReshareMessageProto(
                                    sessionId = sessionID,
                                    hexChainCode = vault.hexChainCode,
                                    serviceName = serviceName,
                                    publicKeyEcdsa = vault.pubKeyECDSA,
                                    oldParties = vault.signers,
                                    encryptionKeyHex = this._encryptionKeyHex,
                                    useVultisigRelay = isRelayEnabled,
                                    oldResharePrefix = vault.resharePrefix,
                                    vaultName = vault.name,
                                    libType = this.vault.libType.toProto(),
                                )
                            )
                        ).encodeBase64()
            }
        }

        loadQrPainter(keygenPayload)

        if (isRelayEnabled) {
            serverUrl = VULTISIG_RELAY_URL
            // start the session
            withContext(Dispatchers.IO) {
                startSession(serverUrl, sessionID, vault.localPartyID)
            }
            startParticipantDiscovery()
        } else {
            // when relay is disabled, start the mediator service
            startMediatorService()
        }
    }

    private fun startParticipantDiscovery() {
        stopParticipantDiscovery()
        discoverParticipantsJob = viewModelScope.launch {
            discoverParticipants(serverUrl, sessionID, vault.localPartyID)
                .collect { participants ->
                    val existingParticipants = uiState.value.participants.toSet()
                    val newParticipants = participants - existingParticipants

                    uiState.update { it.copy(participants = participants) }
                    newParticipants.forEach(::addParticipant)
                }
        }
    }

    private fun stopParticipantDiscovery() {
        discoverParticipantsJob?.cancel()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Timber.d("onReceive: Mediator service started")
                // send a request to local mediator server to start the session
                GlobalScope.launch(Dispatchers.IO) {
                    delay(1000) // back off a second
                    startSession(serverUrl, sessionID, vault.localPartyID)
                }

                startParticipantDiscovery()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun startMediatorService() {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            //Todo Handle older Android versions if needed
            context.registerReceiver(serviceStartedReceiver, filter)
        }

        MediatorService.start(context, serviceName)
    }

    private suspend fun startSession(
        serverAddress: String,
        sessionID: String,
        localPartyID: String,
    ) {
        // start the session
        try {
            sessionApi.startSession(serverAddress, sessionID, listOf(localPartyID))
            Timber.d("startSession: Session started")

            val isReshare = uiState.value.isReshareMode
            if (isFastSign || isReshare) {
                if (email != null) {
                    if (password != null) {
                        if (uiState.value.isReshareMode) {
                            val pubKeyEcdsa = if (signerRepository.hasFastSign(vault.pubKeyECDSA))
                                vault.pubKeyECDSA
                            else null

                            signerRepository.joinReshare(
                                JoinReshareRequestJson(
                                    vaultName = vault.name,
                                    publicKeyEcdsa = pubKeyEcdsa,
                                    sessionId = sessionID,
                                    hexEncryptionKey = _encryptionKeyHex,
                                    hexChainCode = vault.hexChainCode,
                                    localPartyId = generateServerPartyId(),
                                    encryptionPassword = password,
                                    email = email,
                                    oldParties = vault.signers,
                                    oldResharePrefix = vault.resharePrefix
                                )
                            )
                        } else {
                            signerRepository.joinKeygen(
                                JoinKeygenRequestJson(
                                    vaultName = vault.name,
                                    sessionId = sessionID,
                                    hexEncryptionKey = _encryptionKeyHex,
                                    hexChainCode = vault.hexChainCode,
                                    localPartyId = generateServerPartyId(),
                                    encryptionPassword = password,
                                    email = email,
                                )
                            )
                        }
                    } else {
                        error("Email is not null, but password is null, this should not happen")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e("startSession: ${e.stackTraceToString()}")
        }
    }

    fun addParticipant(participant: String) {
        val currentList = uiState.value.selection
        if (currentList.contains(participant)) return
        uiState.update { it.copy(selection = currentList + participant) }
    }

    fun removeParticipant(participant: String) {
        uiState.update { it.copy(selection = it.selection - participant) }
    }

    private fun moveToState(nextState: KeygenFlowState) {
        viewModelScope.launch {
            stopParticipantDiscovery()
            uiState.update { it.copy(currentState = nextState) }
        }
    }

    fun moveToKeygen() {
        viewModelScope.launch {
            setLoading(true)
            withContext(Dispatchers.IO) {
                startKeygen()
            }
            setLoading(false)
            moveToState(KeygenFlowState.KEYGEN)
        }
    }

    fun finishPeerDiscovery() {
        if (setupType == VaultSetupType.FAST) {
            moveToKeygen()
        } else {
            uiState.update {
                it.copy(
                    deletedParticipants = (vault.signers - uiState.value.selection).toList()
                )
            }
            moveToState(KeygenFlowState.DEVICE_CONFIRMATION)
        }
    }

    private suspend fun startKeygen() {
        try {
            val keygenCommittee = uiState.value.selection
            sessionApi.startWithCommittee(serverUrl, sessionID, keygenCommittee)
            Timber.tag("KeygenDiscoveryViewModel").d("startKeygen: Keygen started")

        } catch (e: Exception) {
            Timber.tag("KeygenDiscoveryViewModel").e("startKeygen: %s", e.stackTraceToString())
        }
    }

    private fun setLoading(isLoading: Boolean) {
        uiState.update {
            it.copy(
                isLoading = isLoading
            )
        }
    }

    fun changeNetworkPromptOption(option: NetworkPromptOption) {
        if (uiState.value.networkOption == option) return

        uiState.update { it.copy(networkOption = option) }
        serverUrl = when (option) {
            NetworkPromptOption.LOCAL -> LOCAL_MEDIATOR_SERVER_URL
            NetworkPromptOption.INTERNET -> VULTISIG_RELAY_URL
        }
        viewModelScope.launch(Dispatchers.IO) {
            updateKeygenPayload()
        }
    }

    internal fun shareQRCode(activity: Context) {
        val qrBitmap = shareQrBitmap.value ?: return
        activity.share(
            qrBitmap,
            shareFileName(
                vault,
                if (uiState.value.isReshareMode) {
                    ShareType.RESHARE
                } else {
                    ShareType.KEYGEN
                }
            )
        )
    }

    private suspend fun loadQrPainter(keygenPayload: String) {
        withContext(Dispatchers.IO) {
            val qrBitmap = generateQrBitmap(keygenPayload, Color.Black, Color.White, null)
            val bitmapPainter = BitmapPainter(
                qrBitmap.asImageBitmap(), filterQuality = FilterQuality.None
            )
            uiState.update { it.copy(qrBitmapPainter = bitmapPainter) }
        }
    }

    internal fun saveShareQrBitmap(
        bitmap: Bitmap,
        color: Int,
        title: String,
        description: String,
        logo: Bitmap,
    ) = viewModelScope.launch {
        val qrBitmap = withContext(Dispatchers.IO) {
            makeQrCodeBitmapShareFormat(bitmap, color, logo, title, description)
        }
        shareQrBitmap.value?.recycle()
        shareQrBitmap.value = qrBitmap
    }


    override fun onCleared() {
        viewModelScope.launch {
            stopParticipantDiscovery()
        }
        super.onCleared()
    }
}