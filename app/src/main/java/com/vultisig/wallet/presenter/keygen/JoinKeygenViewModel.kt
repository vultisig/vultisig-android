package com.vultisig.wallet.presenter.keygen

import android.annotation.SuppressLint
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.vultisig.wallet.common.DeepLinkHelper
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.models.PeerDiscoveryPayload
import com.vultisig.wallet.models.TssAction
import com.vultisig.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL
import javax.inject.Inject

enum class JoinKeygenState {
    DiscoveryingSessionID, DiscoverService, JoinKeygen, WaitingForKeygenStart, Keygen, FailedToStart, ERROR
}

@HiltViewModel
class JoinKeygenViewModel @Inject constructor(
    private val vaultDB: VaultDB,
    private val gson: Gson,
) : ViewModel() {
    private var _vault: Vault = Vault("new vault")
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
    private var jobWaitingForKeygenStart: Job? = null

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
            vaultDB = vaultDB
        )

    fun setData(vault: Vault) {
        _vault = vault
        if (_vault.localPartyID.isEmpty()) {
            _vault.localPartyID = Utils.deviceName
        }
        _localPartyID = _vault.localPartyID
    }

    fun setScanResult(content: String) {
        try {
            val qrCodeContent = DeepLinkHelper(content).getJsonData()
            qrCodeContent ?: run {
                throw Exception("invalid QR code")
            }
            when (val payload = PeerDiscoveryPayload.fromJson(gson, qrCodeContent)) {
                is PeerDiscoveryPayload.Keygen -> {
                    this._action = TssAction.KEYGEN
                    this._sessionID = payload.keygenMessage.sessionID
                    this._hexChainCode = payload.keygenMessage.hexChainCode
                    this._vault.hexChainCode = this._hexChainCode
                    this._serviceName = payload.keygenMessage.serviceName
                    this._useVultisigRelay = payload.keygenMessage.useVultisigRelay
                    this._encryptionKeyHex = payload.keygenMessage.encryptionKeyHex
                    _vault.name = payload.keygenMessage.vaultName
                }

                is PeerDiscoveryPayload.Reshare -> {
                    this._action = TssAction.ReShare
                    this._sessionID = payload.reshareMessage.sessionID
                    this._hexChainCode = payload.reshareMessage.hexChainCode
                    this._vault.hexChainCode = this._hexChainCode
                    this._serviceName = payload.reshareMessage.serviceName
                    this._useVultisigRelay = payload.reshareMessage.useVultisigRelay
                    this._encryptionKeyHex = payload.reshareMessage.encryptionKeyHex
                    this._oldCommittee = payload.reshareMessage.oldParties
                    // trying to find out whether the device already have a vault with the same public key
                    // if the device has a vault with the same public key , then automatically switch to it
                    vaultDB.selectAll().forEach() {
                        if (it.pubKeyECDSA == payload.reshareMessage.pubKeyECDSA) {
                            _vault = it
                            _localPartyID = it.localPartyID
                            return@forEach
                        }
                    }
                    if (_vault.pubKeyECDSA.isEmpty()) {
                        _vault.hexChainCode = payload.reshareMessage.hexChainCode
                    } else {
                        if (_vault.pubKeyECDSA != payload.reshareMessage.pubKeyECDSA) {
                            errorMessage.value =  "Wrong vault"
                            currentState.value = JoinKeygenState.FailedToStart
                        }
                    }
                }
            }
            if (_useVultisigRelay) {
                this._serverAddress = Endpoints.VULTISIG_RELAY
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

    private fun onServerAddressDiscovered(addr: String) {
        _serverAddress = addr
        currentState.value = JoinKeygenState.JoinKeygen
        // discovery finished
        _discoveryListener?.let { _nsdManager?.stopServiceDiscovery(it) }
    }

    fun discoveryMediator(nsdManager: NsdManager) {
        _discoveryListener =
            MediatorServiceDiscoveryListener(nsdManager, _serviceName, ::onServerAddressDiscovered)
        _nsdManager = nsdManager
        nsdManager.discoverServices(
            "_http._tcp.", NsdManager.PROTOCOL_DNS_SD, _discoveryListener
        )
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

