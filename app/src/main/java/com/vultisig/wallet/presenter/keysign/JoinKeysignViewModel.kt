package com.vultisig.wallet.presenter.keysign

import android.net.nsd.NsdManager
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import com.google.common.reflect.TypeToken
import com.google.gson.Gson
import com.vultisig.wallet.common.DeepLinkHelper
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.keygen.JoinKeygenState
import com.vultisig.wallet.presenter.keygen.MediatorServiceDiscoveryListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.HttpURLConnection
import java.net.URL

enum class JoinKeysignState {
    DiscoveryingSessionID,
    DiscoverService,
    JoinKeysign,
    WaitingForKeysignStart,
    Keysign,
    FailedToStart,
    Error
}

class JoinKeysignViewModel : ViewModel() {
    private var _currentVault: Vault = Vault("temp vault")
    var currentState: MutableState<JoinKeysignState> =
        mutableStateOf(JoinKeysignState.DiscoveryingSessionID)
    var errorMessage: MutableState<String> = mutableStateOf("")
    private var _localPartyID: String = ""
    private var _sessionID: String = ""
    private var _serviceName: String = ""
    private var _useVultisigRelay: Boolean = false
    private var _encryptionKeyHex: String = ""
    private var _serverAddress: String = ""
    private var _keysignCommittee: List<String> = emptyList()
    private var _discoveryListener: MediatorServiceDiscoveryListener? = null
    private var _nsdManager: NsdManager? = null
    fun setData(vault: Vault) {
        _currentVault = vault
        _localPartyID = vault.localPartyID
    }

    fun setScanResult(content: String) {
        val qrCodeContent = DeepLinkHelper(content).getJsonData()
        qrCodeContent ?: run {
            throw Exception("Invalid QR code content")
        }
        val payload = KeysignMesssage.fromJson(qrCodeContent)
        this._sessionID = payload.sessionID
        this._serviceName = payload.serviceName
        this._useVultisigRelay = payload.usevultisigRelay
        this._encryptionKeyHex = payload.encryptionKeyHex
        if (_currentVault.pubKeyECDSA != payload.payload.vaultPublicKeyECDSA) {
            currentState.value = JoinKeysignState.Error
        }
        if (_useVultisigRelay) {
            this._serverAddress = Endpoints.VULTISIG_RELAY
            currentState.value = JoinKeysignState.JoinKeysign
        } else {
            currentState.value = JoinKeysignState.DiscoverService
        }

    }

    private fun onServerAddressDiscovered(addr: String) {
        _serverAddress = addr
        currentState.value = JoinKeysignState.JoinKeysign
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

    suspend fun joinKeysign() {
        withContext(Dispatchers.IO) {
            try {
                val serverUrl = URL("${_serverAddress}/$_sessionID")
                Log.d("JoinKeysignViewModel", "Joining keysign at $serverUrl")
                val payload = listOf(_localPartyID)

                val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
                val request = okhttp3.Request.Builder()
                    .method(
                        "POST",
                        Gson().toJson(payload).toRequestBody("application/json".toMediaType())
                    )
                    .url(serverUrl)
                    .build()
                val resp = client.newCall(request).execute().use {
                    Log.d("JoinKeysignViewModel", "Join keysign: Response code: ${it.code}")
                }
                currentState.value = JoinKeysignState.WaitingForKeysignStart
            } catch (e: Exception) {
                Log.e("JoinKeysignViewModel", "Failed to join keysign: ${e.stackTraceToString()}")
                errorMessage.value = "Failed to join keysign"
                currentState.value = JoinKeysignState.FailedToStart
            }
        }
    }
    suspend fun waitForKeysignToStart() {
        withContext(Dispatchers.IO) {
            while (true) {
                if (checkKeygenStarted()) {
                    currentState.value = JoinKeysignState.Keysign
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
            Log.d("JoinKeysignViewModel", "Checking keysign start at $serverURL")
            val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder().url(serverURL).get().build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_OK -> {
                        Log.d("JoinKeysignViewModel", "Keygen started")
                        response.body?.let {
                            val result = it.string()
                            val tokenType = object : TypeToken<List<String>>() {}.type
                            this._keysignCommittee = Gson().fromJson(result, tokenType)
                            if (this._keysignCommittee.contains(_localPartyID)) {
                                return true
                            }
                        }
                    }

                    else -> {
                        Log.d(
                            "JoinKeysignViewModel",
                            "Failed to check start keysign: Response code: ${response.code}"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("JoinKeysignViewModel", "Failed to check keysign start: ${e.stackTraceToString()}")
        }
        return false
    }
}