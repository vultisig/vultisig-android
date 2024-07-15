@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.presenter.keygen

import android.annotation.SuppressLint
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.R
import com.vultisig.wallet.common.DeepLinkHelper
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.common.asUiText
import com.vultisig.wallet.common.unzipZlib
import com.vultisig.wallet.data.mappers.KeygenMessageFromProtoMapper
import com.vultisig.wallet.data.mappers.ReshareMessageFromProtoMapper
import com.vultisig.wallet.data.models.proto.v1.KeygenMessageProto
import com.vultisig.wallet.data.models.proto.v1.ReshareMessageProto
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.models.PeerDiscoveryPayload
import com.vultisig.wallet.models.TssAction
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class JoinKeygenState {
    DiscoveryingSessionID, DiscoverService, JoinKeygen, WaitingForKeygenStart, Keygen, FailedToStart, ERROR
}

@HiltViewModel
internal class JoinKeygenViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val gson: Gson,
    private val protoBuf: ProtoBuf,
    private val mapKeygenMessageFromProto: KeygenMessageFromProtoMapper,
    private val mapReshareMessageFromProto: ReshareMessageFromProtoMapper,
    private val saveVault: SaveVaultUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
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

    var currentState: MutableState<JoinKeygenState> =
        mutableStateOf(JoinKeygenState.DiscoveryingSessionID)
    var errorMessage: MutableState<String> = mutableStateOf("")
    val generatingKeyViewModel: GeneratingKeyViewModel
        get() = GeneratingKeyViewModel(
            _vault,
            _action,
            _keygenCommittee,
            _oldCommittee.filter { _keygenCommittee.contains(it) },
            _serverAddress,
            _sessionID,
            _encryptionKeyHex,
            gson = gson,
            oldResharePrefix = _oldResharePrefix,
            navigator = navigator,
            saveVault = saveVault,
            lastOpenedVaultRepository = lastOpenedVaultRepository,
            vaultDataStoreRepository = vaultDataStoreRepository,
        )

    @OptIn(ExperimentalEncodingApi::class)
    fun setScanResult(qrBase64: String) {
        viewModelScope.launch {
            if (_vault.name.isEmpty()) {
                val allSize = vaultRepository.getAll().size
                _vault.name = "New Vault ${allSize + 1}"
            }

            if (_vault.localPartyID.isEmpty()) {
                _vault.localPartyID = Utils.deviceName
            }
            _localPartyID = _vault.localPartyID

            try {
                val content = Base64.UrlSafe.decode(qrBase64.toByteArray())
                    .decodeToString()

                val deepLink = DeepLinkHelper(content)

                val qrCodeContent =
                    deepLink.getJsonData() ?: error("Invalid QR code")

                val contentBytes = qrCodeContent.decodeBase64Bytes().unzipZlib()

                val payload = when (deepLink.getTssAction()) {
                    TssAction.KEYGEN -> PeerDiscoveryPayload.Keygen(
                        mapKeygenMessageFromProto(
                            protoBuf.decodeFromByteArray<KeygenMessageProto>(contentBytes)
                        )
                    )

                    TssAction.ReShare -> PeerDiscoveryPayload.Reshare(
                        mapReshareMessageFromProto(
                            protoBuf.decodeFromByteArray<ReshareMessageProto>(contentBytes)
                        )
                    )

                    else -> error("Invalid TssAction")
                }
                Timber.d("Decoded KeygenMessage: $payload")

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
                        val allVaults = vaultRepository.getAll()
                        allVaults.forEach {
                            if (it.pubKeyECDSA == payload.reshareMessage.pubKeyECDSA) {
                                _vault = it
                                _localPartyID = it.localPartyID
                                return@forEach
                            }
                        }
                        if (_vault.pubKeyECDSA.isEmpty()) {
                            _vault.hexChainCode = payload.reshareMessage.hexChainCode
                            _vault.name = payload.reshareMessage.vaultName
                            allVaults.find { it.name == _vault.name }?.let {
                                errorMessage.value =
                                    R.string.vault_already_exist.asUiText(_vault.name).toString()
                                currentState.value = JoinKeygenState.FailedToStart
                            }
                        } else {
                            if (_vault.pubKeyECDSA != payload.reshareMessage.pubKeyECDSA) {
                                errorMessage.value = "Wrong vault"
                                currentState.value = JoinKeygenState.FailedToStart
                            }
                        }
                    }
                }
                if (_useVultisigRelay) {
                    this@JoinKeygenViewModel._serverAddress = Endpoints.VULTISIG_RELAY
                    currentState.value = JoinKeygenState.JoinKeygen
                } else {
                    currentState.value = JoinKeygenState.DiscoverService
                }
            } catch (e: Exception) {
                Timber.d("Failed to parse QR code, error: $e")
                errorMessage.value = "Failed to parse QR code"
                currentState.value = JoinKeygenState.FailedToStart
            }
        }
    }

    private fun onServerAddressDiscovered(addr: String) {
        _serverAddress = addr
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
                val serverUrl = URL("${_serverAddress}/$_sessionID")
                Timber.d("Joining keygen at $serverUrl")
                val conn = serverUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val payload = listOf(_localPartyID)
                gson.toJson(payload).also {
                    conn.outputStream.write(it.toByteArray())
                }
                val responseCode = conn.responseCode
                Timber.d("Join Keygen: Response code: $responseCode")
                conn.disconnect()
                currentState.value = JoinKeygenState.WaitingForKeygenStart
            } catch (e: Exception) {
                Timber.e("Failed to join keygen: ${e.stackTraceToString()}")
                errorMessage.value = "Failed to join keygen"
                currentState.value = JoinKeygenState.FailedToStart
            }
        }
    }

    suspend fun waitForKeygenToStart() {
        jobWaitingForKeygenStart = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    if (checkKeygenStarted()) {
                        currentState.value = JoinKeygenState.Keygen
                        return@withContext
                    }
                    // backoff 1s
                    Thread.sleep(1000)
                }
            }
        }
    }

    fun cleanUp() {
        jobWaitingForKeygenStart?.cancel()
    }

    @SuppressLint("BinaryOperationInTimber")
    private fun checkKeygenStarted(): Boolean {
        try {
            val serverURL = "$_serverAddress/start/$_sessionID"
            Timber.d("Checking keygen start at $serverURL")
            val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder().url(serverURL).get().build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_OK -> {
                        Timber.tag("JoinKeygenViewModel").d("Keygen started")
                        response.body?.let {
                            val result = it.string()
                            val tokenType = object : TypeToken<List<String>>() {}.type
                            this._keygenCommittee = gson.fromJson(result, tokenType)
                            if (this._keygenCommittee.contains(_localPartyID)) {
                                return true
                            }
                        }
                    }

                    else -> {
                        Timber.tag("JoinKeygenViewModel")
                            .d("Failed to check start keygen: Response code: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.tag("JoinKeygenViewModel")
                .e("Failed to check keygen start: ${e.stackTraceToString()}")
        }
        return false
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
        serviceInfo?.let {
            val addr = it.host
            if (addr !is Inet4Address) {
                return
            }
            if (addr.isLoopbackAddress) {
                return
            }
            addr.hostAddress?.let {
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

