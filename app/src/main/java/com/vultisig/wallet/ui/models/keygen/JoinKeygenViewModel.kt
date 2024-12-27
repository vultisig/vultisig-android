@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keygen

import android.annotation.SuppressLint
import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.mappers.KeygenMessageFromProtoMapper
import com.vultisig.wallet.data.mappers.ReshareMessageFromProtoMapper
import com.vultisig.wallet.data.models.PeerDiscoveryPayload
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.repositories.vault.VaultMetadataRepo
import com.vultisig.wallet.data.usecases.DecompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import java.net.Inet4Address
import java.util.UUID
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

private const val WARNING_TIMEOUT = 10000L

enum class JoinKeygenState {
    DiscoveringSessionID, DiscoverService, JoinKeygen, WaitingForKeygenStart, Keygen, FailedToStart
}



@HiltViewModel
internal class JoinKeygenViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val protoBuf: ProtoBuf,
    private val mapKeygenMessageFromProto: KeygenMessageFromProtoMapper,
    private val mapReshareMessageFromProto: ReshareMessageFromProtoMapper,
    private val saveVault: SaveVaultUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val decompressQr: DecompressQrUseCase,
    private val sessionApi: SessionApi,
    @ApplicationContext private val context: Context,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val vaultMetadataRepo: VaultMetadataRepo,
    private val vultiSignerRepository: VultiSignerRepository,
) : ViewModel() {
    private var _vault: Vault = Vault(id = UUID.randomUUID().toString(), "")
    private var _localPartyID: String = ""
    private var _action: TssAction = TssAction.KEYGEN
    private var _sessionID: String = ""
    private var _hexChainCode: String = ""
    private var _serviceName: String = ""
    private var _useVultisigRelay: Boolean = false
    private var _encryptionKeyHex: String = ""
    private var _oldCommittee: List<String> = emptyList()
    private var _serverAddress: String = ""
    private var _nsdManager: NsdManager? = null
    private var _discoveryListener: MediatorServiceDiscoveryListener? = null
    private var _keygenCommittee: List<String> = emptyList()
    private var _oldResharePrefix: String = ""
    private var jobWaitingForKeygenStart: Job? = null
    private var _isDiscoveryListenerRegistered = false
    var operationMode = mutableStateOf(OperationMode.KEYGEN)

    var currentState: MutableState<JoinKeygenState> =
        mutableStateOf(JoinKeygenState.DiscoveringSessionID)
    var errorMessage: MutableState<UiText> =
        mutableStateOf(UiText.StringResource(R.string.default_error))
    val warningHostState = SnackbarHostState()

    private val warningLauncher =
        viewModelScope.launch {
            delay(WARNING_TIMEOUT)
            warningHostState.showSnackbar(
                errorMessage.value.asString(context),
                duration = SnackbarDuration.Indefinite
            )
        }

    val generatingKeyViewModel: GeneratingKeyViewModel
        get() = GeneratingKeyViewModel(
            _vault,
            _action,
            _keygenCommittee,
            _oldCommittee.filter { _keygenCommittee.contains(it) },
            _serverAddress,
            _sessionID,
            _encryptionKeyHex,
            vaultSetupType = null,
            oldResharePrefix = _oldResharePrefix,
            isInitiatingDevice = false,
            navigator = navigator,
            saveVault = saveVault,
            lastOpenedVaultRepository = lastOpenedVaultRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
            context = context,
            sessionApi = sessionApi,
            isReshareMode = operationMode.value.isReshare(),
            encryption = encryption,
            featureFlagApi = featureFlagApi,
            vaultPasswordRepository = vaultPasswordRepository,
            vaultMetadataRepo = vaultMetadataRepo,
            vultiSignerRepository = vultiSignerRepository,
        )

    @OptIn(ExperimentalEncodingApi::class)
    fun setScanResult(qrBase64: String) {
        viewModelScope.launch {
            if (_vault.name.isEmpty()) {
                val allSize = vaultRepository.getAll().size
                _vault.name = "New Vault ${allSize + 1}"
            }

            if (_vault.localPartyID.isEmpty()) {
                _vault.localPartyID = Utils.deviceName(context)
            }
            _localPartyID = _vault.localPartyID

            try {
                val content = Base64.UrlSafe.decode(qrBase64.toByteArray())
                    .decodeToString()

                val deepLink = DeepLinkHelper(content)

                val qrCodeContent =
                    deepLink.getJsonData() ?: error("Invalid QR code")

                val contentBytes = decompressQr(qrCodeContent.decodeBase64Bytes())

                val payload = when (deepLink.getTssAction()) {
                    TssAction.KEYGEN -> {
                        operationMode.value = OperationMode.KEYGEN
                        PeerDiscoveryPayload.Keygen(
                            mapKeygenMessageFromProto(
                                protoBuf.decodeFromByteArray<KeygenMessageProto>(contentBytes)
                            )
                        )
                }

                    TssAction.ReShare ->{
                        operationMode.value = OperationMode.RESHARE
                        PeerDiscoveryPayload.Reshare(
                            mapReshareMessageFromProto(
                                protoBuf.decodeFromByteArray<ReshareMessageProto>(contentBytes)
                            )
                        )
                    }

                    else -> error("Invalid TssAction")
                }
                Timber.d("Decoded KeygenMessage: $payload")

                val allVaults = vaultRepository.getAll()

                when (payload) {
                    is PeerDiscoveryPayload.Keygen -> {
                        this@JoinKeygenViewModel._action = TssAction.KEYGEN
                        this@JoinKeygenViewModel._sessionID = payload.keygenMessage.sessionID
                        this@JoinKeygenViewModel._hexChainCode = payload.keygenMessage.hexChainCode
                        this@JoinKeygenViewModel._vault.hexChainCode = this@JoinKeygenViewModel._hexChainCode
                        this@JoinKeygenViewModel._serviceName = payload.keygenMessage.serviceName
                        this@JoinKeygenViewModel._useVultisigRelay = payload.keygenMessage.useVultisigRelay
                        this@JoinKeygenViewModel._encryptionKeyHex = payload.keygenMessage.encryptionKeyHex
                        _vault.name = payload.keygenMessage.vaultName
                        _vault.libType = payload.keygenMessage.libType
                        allVaults.find { it.name == _vault.name }?.let {
                            errorMessage.value = UiText.FormattedText(
                                R.string.vault_already_exist,
                                listOf(_vault.name)
                            )
                            currentState.value = JoinKeygenState.FailedToStart
                            return@launch
                        }
                    }

                    is PeerDiscoveryPayload.Reshare -> {
                        this@JoinKeygenViewModel._action = TssAction.ReShare
                        this@JoinKeygenViewModel._sessionID = payload.reshareMessage.sessionID
                        this@JoinKeygenViewModel._hexChainCode = payload.reshareMessage.hexChainCode
                        this@JoinKeygenViewModel._vault.hexChainCode = this@JoinKeygenViewModel._hexChainCode
                        this@JoinKeygenViewModel._serviceName = payload.reshareMessage.serviceName
                        this@JoinKeygenViewModel._useVultisigRelay = payload.reshareMessage.useVultisigRelay
                        this@JoinKeygenViewModel._encryptionKeyHex = payload.reshareMessage.encryptionKeyHex
                        this@JoinKeygenViewModel._oldCommittee = payload.reshareMessage.oldParties
                        this@JoinKeygenViewModel._oldResharePrefix = payload.reshareMessage.oldResharePrefix
                        // trying to find out whether the device already have a vault with the same public key
                        // if the device has a vault with the same public key , then automatically switch to it
                        allVaults.forEach {
                            if (it.pubKeyECDSA == payload.reshareMessage.pubKeyECDSA) {
                                _vault = it
                                _localPartyID = it.localPartyID
                                return@forEach
                            }
                        }
                        _vault.libType = payload.reshareMessage.libType
                        if (_vault.pubKeyECDSA.isEmpty()) {
                            _vault.hexChainCode = payload.reshareMessage.hexChainCode
                            _vault.name = payload.reshareMessage.vaultName
                            allVaults.find { it.name == _vault.name }?.let {
                                errorMessage.value = UiText.FormattedText(
                                    R.string.vault_already_exist,
                                    listOf(_vault.name)
                                )
                                currentState.value = JoinKeygenState.FailedToStart
                                return@launch
                            }
                        } else {
                            if (_vault.pubKeyECDSA != payload.reshareMessage.pubKeyECDSA) {
                                errorMessage.value =
                                    UiText.StringResource(R.string.join_keysign_missing_required_vault)
                                currentState.value = JoinKeygenState.FailedToStart
                                return@launch
                            }
                            if (_vault.resharePrefix != payload.reshareMessage.oldResharePrefix) {
                                errorMessage.value =
                                    UiText.StringResource(R.string.join_keygen_wrong_reshare)
                                currentState.value = JoinKeygenState.FailedToStart
                                return@launch
                            }
                        }
                    }
                }
                if (_useVultisigRelay) {
                    this@JoinKeygenViewModel._serverAddress = Endpoints.VULTISIG_RELAY_URL
                    currentState.value = JoinKeygenState.JoinKeygen
                } else {
                    currentState.value = JoinKeygenState.DiscoverService
                }
            } catch (e: Exception) {
                Timber.d("Failed to parse QR code, error: $e")
                errorMessage.value = UiText.StringResource(R.string.join_keygen_invalid_qr_code)
                currentState.value = JoinKeygenState.FailedToStart
            }
        }
    }

    private fun onServerAddressDiscovered(address: String) {
        _serverAddress = address
        currentState.value = JoinKeygenState.JoinKeygen
        // discovery finished
        _discoveryListener?.let {
            if (_isDiscoveryListenerRegistered) {
                _nsdManager?.stopServiceDiscovery(it)
                _isDiscoveryListenerRegistered = false
            }
        }
    }

    fun discoveryMediator(nsdManager: NsdManager) {
        _discoveryListener =
            MediatorServiceDiscoveryListener(nsdManager, _serviceName, ::onServerAddressDiscovered)
        _nsdManager = nsdManager
        nsdManager.discoverServices(
            "_http._tcp.", NsdManager.PROTOCOL_DNS_SD, _discoveryListener
        )
        _isDiscoveryListenerRegistered = true
    }

    suspend fun joinKeygen() {
        withContext(Dispatchers.IO) {
            try {
                Timber.d("Joining ${operationMode.value.name}")
                sessionApi.startSession(_serverAddress, _sessionID, listOf(_localPartyID))
                Timber.d("Join ${operationMode.value.name} ")
                currentState.value = JoinKeygenState.WaitingForKeygenStart
            } catch (e: Exception) {
                Timber.e("Failed to join ${operationMode.value.name}}: ${e.stackTraceToString()}")
                errorMessage.value =
                    R.string.join_keygen_failed.asUiText(
                        UiText.StringResource(
                            if (operationMode.value == OperationMode.RESHARE) R.string.join_keygen_screen_reshare else R.string.join_keygen_screen_keygen
                        ).asString(context)
                    )
            }
        }
    }

    suspend fun waitForKeygenToStart() {
        warningLauncher.cancel()
        warningHostState.currentSnackbarData?.dismiss()
        jobWaitingForKeygenStart = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    if (checkKeygenStarted()) {
                        currentState.value = JoinKeygenState.Keygen
                        return@withContext
                    }
                    // backoff 1s
                    delay(1000)
                }
            }
        }
    }

    fun cleanUp() {
        jobWaitingForKeygenStart?.cancel()
    }

    @SuppressLint("BinaryOperationInTimber")
    private suspend fun checkKeygenStarted(): Boolean {
        try {
            this._keygenCommittee = sessionApi.checkCommittee(_serverAddress, _sessionID)
            if (this._keygenCommittee.contains(_localPartyID)) {
                Timber.tag("JoinKeygenViewModel").d("${operationMode.value.name}} started")
                return true
            }
        } catch (e: Exception) {
            Timber.tag("JoinKeygenViewModel")
                .e("Failed to check ${operationMode.value} start: ${e.stackTraceToString()}")
        }
        return false
    }

    enum class OperationMode {
        KEYGEN,
        RESHARE;

        fun isReshare(): Boolean {
            return this == OperationMode.RESHARE
        }
    }
}

class MediatorServiceDiscoveryListener(
    private val nsdManager: NsdManager,
    private val serviceName: String,
    private val onServerAddressDiscovered: (String) -> Unit,
) : NsdManager.DiscoveryListener, NsdManager.ResolveListener {
    override fun onDiscoveryStarted(regType: String) {
        Timber.tag("JoinKeygenViewModel").d("Service discovery started, regType: $regType")
    }

    override fun onServiceFound(service: NsdServiceInfo) {
        Timber.tag("JoinKeygenViewModel").d("Service found: %s", service.serviceName)
        if (service.serviceName == serviceName) {
            Timber.tag("JoinKeygenViewModel").d("Service found: %s", service.serviceName)
            nsdManager.resolveService(
                service,
                MediatorServiceDiscoveryListener(nsdManager, serviceName, onServerAddressDiscovered)
            )
        }
    }

    override fun onServiceLost(service: NsdServiceInfo) {
        Timber.tag("JoinKeygenViewModel")
            .d("Service lost: %s, port: %d", service.serviceName, service.port)
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Timber.tag("JoinKeygenViewModel").d("Discovery stopped: $serviceType")
    }

    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Timber.tag("JoinKeygenViewModel")
            .d("Failed to start discovery: $serviceType, error: $errorCode")
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Timber.tag("JoinKeygenViewModel")
            .d("Failed to stop discovery: $serviceType, error: $errorCode")
    }

    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Timber.tag("JoinKeygenViewModel")
            .d("Failed to resolve service: ${serviceInfo?.serviceName} , error: $errorCode")
    }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {
        Timber.tag("JoinKeygenViewModel")
            .d("Service resolved: ${serviceInfo?.serviceName} ,address: ${serviceInfo?.host?.address.toString()} , port: ${serviceInfo?.port}")
        serviceInfo?.let { it ->
            val address = it.host
            if (address !is Inet4Address) {
                return
            }
            if (address.isLoopbackAddress) {
                return
            }
            address.hostAddress?.let {
                // This is a workaround for the emulator
                if (it == "10.0.2.16") {
                    onServerAddressDiscovered("http://192.168.1.35:18080")
                    return
                }
                onServerAddressDiscovered("http://${it}:${serviceInfo.port}")
            }

        }

    }

}

