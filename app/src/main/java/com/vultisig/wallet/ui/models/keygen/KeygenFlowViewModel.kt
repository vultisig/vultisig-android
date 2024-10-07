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
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ParticipantDiscovery
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.models.signer.JoinKeygenRequestJson
import com.vultisig.wallet.data.api.models.signer.JoinReshareRequestJson
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.MakeQrCodeBitmapShareFormat
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.data.utils.ServerUtils.LOCAL_PARTY_ID_PREFIX
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

enum class KeygenFlowState {
    PEER_DISCOVERY, DEVICE_CONFIRMATION, KEYGEN, ERROR, SUCCESS
}

internal data class KeygenFlowUiModel(
    val currentState: KeygenFlowState = KeygenFlowState.PEER_DISCOVERY,
    val isReshareMode: Boolean,
    val selection: List<String> = emptyList(),
    val deletedParticipants: List<String> = emptyList(),
    val participants: List<String> = emptyList(),
    val qrBitmapPainter: BitmapPainter? = null,
    val networkOption: NetworkPromptOption = NetworkPromptOption.INTERNET,
    val vaultSetupType: VaultSetupType = VaultSetupType.SECURE,
) {
    val isContinueButtonEnabled =
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

@HiltViewModel
internal class KeygenFlowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val compressQr: CompressQrUseCase,
    private val saveVault: SaveVaultUseCase,
    private val vaultRepository: VaultRepository,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val signerRepository: VultiSignerRepository,
    @ApplicationContext private val context: Context,
    private val protoBuf: ProtoBuf,
    private val sessionApi: SessionApi,
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat,
    private val generateQrBitmap: GenerateQrBitmap,
    private val encryption: Encryption,
) : ViewModel() {

    private val setupType = VaultSetupType.fromInt(
        savedStateHandle.get<Int>(ARG_VAULT_SETUP_TYPE) ?: 0
    )

    val uiState = MutableStateFlow(
        KeygenFlowUiModel(
            vaultSetupType = setupType,
            isReshareMode = false
        )
    )

    private val sessionID: String = UUID.randomUUID().toString() // generate a random UUID
    private val serviceName: String = "vultisigApp-${Random.nextInt(1, 1000)}"
    private var serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var participantDiscovery: ParticipantDiscovery? = null
    private var action: TssAction = TssAction.KEYGEN
    private var vault: Vault = Vault(id = UUID.randomUUID().toString(), "New Vault")
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex
    private var _oldResharePrefix: String = ""


    private val vaultId: String? = savedStateHandle[Destination.KeygenFlow.ARG_VAULT_ID]
    private val vaultName: String? = savedStateHandle[Destination.KeygenFlow.ARG_VAULT_NAME]
    private val email: String? = savedStateHandle[Destination.ARG_EMAIL]
    private val password: String? = savedStateHandle[Destination.ARG_PASSWORD]
    private val shareQrBitmap = MutableStateFlow<Bitmap?>(null)

    private val isFastSign: Boolean
        get() = setupType.isFast && email != null && password != null

    private val isRelayEnabled =
        MutableStateFlow(uiState.value.networkOption == NetworkPromptOption.INTERNET || isFastSign)

    val localPartyID: String
        get() = vault.localPartyID

    val generatingKeyViewModel: GeneratingKeyViewModel
        get() = GeneratingKeyViewModel(
            vault,
            this.action,
            uiState.value.selection,
            vault.signers.filter { uiState.value.selection.contains(it) },
            serverAddress,
            sessionID,
            _encryptionKeyHex,
            _oldResharePrefix,
            navigator = navigator,
            saveVault = saveVault,
            lastOpenedVaultRepository = lastOpenedVaultRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
            context = context,
            sessionApi = sessionApi,
            isReshareMode = uiState.value.isReshareMode,
            encryption = encryption,
        )

    init {
        viewModelScope.launch {
            setData(vaultId, context.applicationContext)
        }

        viewModelScope.launch {
            uiState.collect {
                isRelayEnabled.value =
                    it.networkOption == NetworkPromptOption.INTERNET || isFastSign
            }
        }

        viewModelScope.launch {
            if (setupType == VaultSetupType.FAST) {
                uiState.map { it.selection }
                    .collect {
                        if (it.size == 2) {
                            finishPeerDiscovery()
                        }
                    }
            }
        }
    }

    private suspend fun setData(vaultId: String?, context: Context) {
        // start mediator server

        val vault = if (vaultId == null) {
            // generate
            vaultName ?: error("No vault name provided")
            Vault(id = UUID.randomUUID().toString(), vaultName)
        } else {
            // reshare
            vaultRepository.get(vaultId) ?: error("No vault with id $vaultId")
        }

        val action = if (vault.pubKeyECDSA.isEmpty()) {
            uiState.value = uiState.value.copy(isReshareMode = false)
            TssAction.KEYGEN
        } else {
            uiState.value = uiState.value.copy(isReshareMode = true)
            TssAction.ReShare
        }
        serverAddress = Endpoints.VULTISIG_RELAY
        this.action = action
        this.vault = vault
        if (this.vault.hexChainCode.isEmpty()) {
            val secureRandom = SecureRandom()
            val randomBytes = ByteArray(32)
            secureRandom.nextBytes(randomBytes)
            this.vault.hexChainCode = randomBytes.joinToString("") { "%02x".format(it) }
        }
        if (this.vault.localPartyID.isEmpty()) {
            this.vault.localPartyID = Utils.deviceName(context)
        }
        uiState.update { it.copy(selection = listOf(this.vault.localPartyID)) }
        _oldResharePrefix = this.vault.resharePrefix
        updateKeygenPayload(context)
    }

    private suspend fun updateKeygenPayload(context: Context) {
        // stop participant discovery
        stopParticipantDiscovery()
        this.participantDiscovery =
            ParticipantDiscovery(serverAddress, sessionID, this.vault.localPartyID, sessionApi)
        viewModelScope.launch {
            participantDiscovery?.participants?.asFlow()?.collect { newList ->
                // add all participants to the selection
                uiState.update { it.copy(participants = newList) }
                for (participant in newList) {
                    addParticipant(participant)
                }
            }
        }
        val isRelayEnabled = isRelayEnabled.value
        val keygenPayload = when (action) {
            TssAction.KEYGEN -> {
                "vultisig://vultisig.com?type=NewVault&tssType=Keygen&jsonData=" +
                        compressQr(
                            protoBuf.encodeToByteArray(
                                KeygenMessageProto(
                                    sessionId = sessionID,
                                    hexChainCode = vault.hexChainCode,
                                    serviceName = serviceName,
                                    encryptionKeyHex = this._encryptionKeyHex,
                                    useVultisigRelay = isRelayEnabled,
                                    vaultName = this.vault.name,
                                )
                            )
                        ).encodeBase64()
            }

            TssAction.ReShare -> {
                "vultisig://vultisig.com?type=NewVault&tssType=Reshare&jsonData=" +
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
                                    vaultName = vault.name
                                )
                            )
                        ).encodeBase64()
            }
        }

        loadQrPainter(keygenPayload)

        if (!isRelayEnabled)
        // when relay is disabled, start the mediator service
            startMediatorService(context)
        else {
            serverAddress = Endpoints.VULTISIG_RELAY
            // start the session
            withContext(Dispatchers.IO) {
                startSession(serverAddress, sessionID, vault.localPartyID)
            }
            // kick off discovery
            participantDiscovery?.discoveryParticipants()
        }
    }

    fun stopParticipantDiscovery() = viewModelScope.launch {
        participantDiscovery?.stop()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Timber.d("onReceive: Mediator service started")
                // send a request to local mediator server to start the session
                GlobalScope.launch(Dispatchers.IO) {
                    delay(1000) // back off a second
                    startSession(serverAddress, sessionID, vault.localPartyID)
                }
                // kick off discovery
                participantDiscovery?.discoveryParticipants()
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun startMediatorService(context: Context) {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            //Todo Handle older Android versions if needed
            context.registerReceiver(serviceStartedReceiver, filter)
        }

        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        intent.putExtra("serverName", serviceName)
        context.startService(intent)
        Timber.d("startMediatorService: Mediator service started")
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

    private fun generateServerPartyId(): String =
        "$LOCAL_PARTY_ID_PREFIX-${Random.nextInt(100, 999)}"

    fun addParticipant(participant: String) {
        val currentList = uiState.value.selection
        if (currentList.contains(participant)) return
        uiState.update { it.copy(selection = currentList + participant) }
    }

    fun removeParticipant(participant: String) {
        uiState.update { it.copy(selection = uiState.value.selection.minus(participant)) }
    }

    private fun moveToState(nextState: KeygenFlowState) {
        viewModelScope.launch {
            stopParticipantDiscovery()
            uiState.update { it.copy(currentState = nextState) }
        }
    }

    fun moveToKeygen() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                startKeygen()
            }
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
            sessionApi.startWithCommittee(serverAddress, sessionID, keygenCommittee)
            Timber.tag("KeygenDiscoveryViewModel").d("startKeygen: Keygen started")

        } catch (e: Exception) {
            Timber.tag("KeygenDiscoveryViewModel").e("startKeygen: %s", e.stackTraceToString())
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun changeNetworkPromptOption(option: NetworkPromptOption, context: Context) {
        if (uiState.value.networkOption == option) return
        uiState.update { it.copy(networkOption = option) }
        serverAddress = when (option) {
            NetworkPromptOption.LOCAL -> {
                "http://127.0.0.1:18080"
            }

            NetworkPromptOption.INTERNET -> {
                Endpoints.VULTISIG_RELAY
            }
        }
        GlobalScope.launch(Dispatchers.IO) {
            updateKeygenPayload(context)
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
        stopParticipantDiscovery()
        super.onCleared()
    }
}