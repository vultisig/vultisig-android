package com.voltix.wallet.presenter.keygen

import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.voltix.wallet.common.Endpoints
import com.voltix.wallet.common.Utils
import com.voltix.wallet.models.PeerDiscoveryPayload
import com.voltix.wallet.models.TssAction
import com.voltix.wallet.models.Vault
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.URL

enum class JoinKeygenState {
    DiscoveryingSessionID,
    DiscoverService,
    JoinKeygen,
    WaitingForKeygenStart,
    Keygen,
    FailedToStart,
    ERROR
}

class JoinKeygenViewModel : ViewModel() {
    private var _vault: Vault = Vault("new vault")
    private var _localPartyID: String = ""
    private var _action: TssAction = TssAction.KEYGEN
    private var _sessionID: String = ""
    private var _hexChainCode: String = ""
    private var _serviceName: String = ""
    private var _useVoltixRelay: Boolean = false
    private var _encryptionKeyHex: String = ""
    private var _oldCommittee: List<String> = emptyList()
    private var _serverAddress: String = ""
    private var _nsdManager: NsdManager? = null
    private var _discoveryListener: MediatorServiceDiscoveryListener? = null
    private var _keygenCommittee: List<String> = emptyList()

    var currentState: MutableState<JoinKeygenState> =
        mutableStateOf(JoinKeygenState.DiscoveryingSessionID)
    var errorMessage: MutableState<String> = mutableStateOf("")
    val generatingKeyViewModel: GeneratingKeyViewModel
        get() = GeneratingKeyViewModel(
            _vault,
            _action,
            _keygenCommittee,
            _vault.signers,
            _serverAddress,
            _sessionID,
            _encryptionKeyHex
        )

    fun setData(vault: Vault) {
        _vault = vault
        if (_vault.LocalPartyID.isEmpty()) {
            _vault.LocalPartyID = Utils.deviceName
        }
        _localPartyID = _vault.LocalPartyID
    }

    fun setScanResult(content: String) {
        try {
            when (val payload = PeerDiscoveryPayload.fromJson(content)) {
                is PeerDiscoveryPayload.Keygen -> {
                    this._action = TssAction.KEYGEN
                    this._sessionID = payload.keygenMessage.sessionID
                    this._hexChainCode = payload.keygenMessage.hexChainCode
                    this._vault.HexChainCode = this._hexChainCode
                    this._serviceName = payload.keygenMessage.serviceName
                    this._useVoltixRelay = payload.keygenMessage.useVoltixRelay
                    this._encryptionKeyHex = payload.keygenMessage.encryptionKeyHex
                }

                is PeerDiscoveryPayload.Reshare -> {
                    this._action = TssAction.ReShare
                    this._sessionID = payload.reshareMessage.sessionID
                    this._hexChainCode = payload.reshareMessage.hexChainCode
                    this._vault.HexChainCode = this._hexChainCode
                    this._serviceName = payload.reshareMessage.serviceName
                    this._useVoltixRelay = payload.reshareMessage.useVoltixRelay
                    this._encryptionKeyHex = payload.reshareMessage.encryptionKeyHex
                    this._oldCommittee = payload.reshareMessage.oldParties
                    if (_vault.PubKeyECDSA.isEmpty()) {
                        _vault.HexChainCode = payload.reshareMessage.hexChainCode
                    } else {
                        if (_vault.PubKeyECDSA != payload.reshareMessage.pubKeyECDSA) {
                            errorMessage.value = "Wrong vault"
                            currentState.value = JoinKeygenState.FailedToStart
                        }
                    }
                }
            }
            if (_useVoltixRelay) {
                this._serverAddress = Endpoints.VOLTIX_RELAY
                currentState.value = JoinKeygenState.JoinKeygen
            } else {
                currentState.value = JoinKeygenState.DiscoverService
            }
        } catch (e: Exception) {
            Log.d("JoinKeygenViewModel", "Failed to parse QR code, error: $e")
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
            "_http._tcp.",
            NsdManager.PROTOCOL_DNS_SD,
            _discoveryListener
        )
    }

    suspend fun joinKeygen() {
        withContext(Dispatchers.IO) {
            try {
                val serverUrl = URL("${_serverAddress}/$_sessionID")
                Log.d("JoinKeygenViewModel", "Joining keygen at $serverUrl")
                val conn = serverUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json")
                conn.doOutput = true
                val payload = listOf(_localPartyID)
                Gson().toJson(payload).also {
                    conn.outputStream.write(it.toByteArray())
                }
                val responseCode = conn.responseCode
                Log.d("KeygenDiscoveryViewModel", "Join Keygen: Response code: $responseCode")
                conn.disconnect()
                currentState.value = JoinKeygenState.WaitingForKeygenStart
            } catch (e: Exception) {
                Log.e("JoinKeygenViewModel", "Failed to join keygen: ${e.stackTraceToString()}")
                errorMessage.value = "Failed to join keygen"
                currentState.value = JoinKeygenState.FailedToStart
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    suspend fun waitForKeygenToStart() {
        withContext(Dispatchers.IO) {
            while (true) {
                if (checkKeygenStarted()) {
                    currentState.value = JoinKeygenState.Keygen
                    return@withContext
                }
                // backoff 1s
                Thread.sleep(1000)
            }
        }
    }

    private fun checkKeygenStarted(): Boolean {
        try {
            val serverURL = "$_serverAddress/start/$_sessionID"
            val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder().url(serverURL).get().build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_OK -> {
                        Log.d("JoinKeygenViewModel", "Keygen started")
                        response.body?.let {
                            val result = it.string()
                            val tokenType = object : TypeToken<List<String>>() {}.type
                            this._keygenCommittee = Gson().fromJson(result, tokenType)
                            return true
                        }
                    }

                    else -> {
                        Log.d(
                            "JoinKeygenViewModel",
                            "Failed to check start keygen: Response code: ${response.code}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("JoinKeygenViewModel", "Failed to check keygen start: ${e.stackTraceToString()}")
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
        Log.d("JoinKeygenViewModel", "Service discovery started, regType: $regType")
    }

    override fun onServiceFound(service: NsdServiceInfo) {
        Log.d(
            "JoinKeygenViewModel",
            "Service found: ${service.serviceName}"
        )
        if (service.serviceName == serviceName) {
            Log.d(
                "JoinKeygenViewModel",
                "Service found: ${service.serviceName}"
            )
            nsdManager.resolveService(
                service,
                MediatorServiceDiscoveryListener(nsdManager, serviceName, onServerAddressDiscovered)
            )
        }
    }

    override fun onServiceLost(service: NsdServiceInfo) {
        Log.d(
            "JoinKeygenViewModel",
            "Service lost: ${service.serviceName}, port:${service.port}"
        )
    }

    override fun onDiscoveryStopped(serviceType: String) {
        Log.d("JoinKeygenViewModel", "Discovery stopped: $serviceType")
    }

    override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.d(
            "JoinKeygenViewModel",
            "Failed to start discovery: $serviceType, error: $errorCode"
        )
    }

    override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
        Log.d(
            "JoinKeygenViewModel",
            "Failed to stop discovery: $serviceType, error: $errorCode"
        )
    }

    override fun onResolveFailed(serviceInfo: NsdServiceInfo?, errorCode: Int) {
        Log.d(
            "JoinKeygenViewModel",
            "Failed to resolve service: ${serviceInfo?.serviceName}, error: $errorCode"
        )
    }

    override fun onServiceResolved(serviceInfo: NsdServiceInfo?) {

        Log.d(
            "JoinKeygenViewModel",
            "Service resolved: ${serviceInfo?.serviceName}, address: ${
                serviceInfo?.host?.address.toString()
            }, port:${serviceInfo?.port}"
        )
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

