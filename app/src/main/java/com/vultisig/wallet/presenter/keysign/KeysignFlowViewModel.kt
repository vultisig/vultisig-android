package com.vultisig.wallet.presenter.keysign

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.common.vultisigRelay
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.mediator.MediatorService
import com.vultisig.wallet.models.TssKeysignType
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.keygen.NetworkPromptOption
import com.vultisig.wallet.presenter.keygen.ParticipantDiscovery
import com.vultisig.wallet.tss.TssKeyType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.net.HttpURLConnection
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

enum class KeysignFlowState {
    PEER_DISCOVERY, KEYSIGN, ERROR,
}

@HiltViewModel
internal class KeysignFlowViewModel @Inject constructor(
    private val vultisigRelay: vultisigRelay,
    private val gson: Gson,
    private val thorChainApi: ThorChainApi,
) : ViewModel() {
    private val _sessionID: String = UUID.randomUUID().toString()
    private val _serviceName: String = "vultisigApp-${Random.nextInt(1, 1000)}"
    private var _serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var _participantDiscovery: ParticipantDiscovery? = null
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex
    private var _currentVault: Vault? = null
    private var _keysignPayload: KeysignPayload? = null
    private val _keysignMessage: MutableState<String> = mutableStateOf("")
    var currentState: MutableState<KeysignFlowState> =
        mutableStateOf(KeysignFlowState.PEER_DISCOVERY)
    var errorMessage: MutableState<String> = mutableStateOf("")
    val selection = MutableLiveData<List<String>>()
    val localPartyID: String?
        get() = _currentVault?.localPartyID
    val keysignMessage: MutableState<String>
        get() = _keysignMessage
    val participants: MutableLiveData<List<String>>
        get() = _participantDiscovery?.participants ?: MutableLiveData(listOf())

    val networkOption: MutableState<NetworkPromptOption> = mutableStateOf(NetworkPromptOption.WIFI)

    val keysignViewModel: KeysignViewModel
        get() = KeysignViewModel(
            vault = _currentVault!!,
            keysignCommittee = selection.value!!,
            serverAddress = _serverAddress,
            sessionId = _sessionID,
            encryptionKeyHex = _encryptionKeyHex,
            messagesToSign = _keysignPayload!!.getKeysignMessages(_currentVault!!),
            keyType = _keysignPayload?.coin?.chain?.TssKeysignType ?: TssKeyType.ECDSA,
            keysignPayload = _keysignPayload!!,
            gson = gson,
            thorChainApi = thorChainApi,
        )

    suspend fun setData(vault: Vault, context: Context, keysignPayload: KeysignPayload) {
        _currentVault = vault
        _keysignPayload = keysignPayload
        this.selection.value = listOf(vault.localPartyID)
        if (vultisigRelay.IsRelayEnabled) {
            _serverAddress = Endpoints.VULTISIG_RELAY
            networkOption.value = NetworkPromptOption.CELLULAR
        }
        updateKeysignPayload(context)
    }

    private suspend fun updateKeysignPayload(context: Context) {
        stopParticipantDiscovery()
        _currentVault ?: run {
            errorMessage.value = "Vault is not set"
            moveToState(KeysignFlowState.ERROR)
            return
        }
        val vault = _currentVault!!
        _participantDiscovery = ParticipantDiscovery(
            _serverAddress,
            _sessionID,
            vault.localPartyID,
            gson
        )

        _keysignMessage.value =
            "vultisig://vultisig.com?type=SignTransaction&vault=${vault.pubKeyECDSA}&jsonData=" + gson.toJson(
                KeysignMesssage(
                    sessionID = _sessionID,
                    serviceName = _serviceName,
                    payload = _keysignPayload!!,
                    encryptionKeyHex = _encryptionKeyHex,
                    usevultisigRelay = vultisigRelay.IsRelayEnabled
                )
            )
        if (!vultisigRelay.IsRelayEnabled) {
            startMediatorService(context)
        } else {
            _serverAddress = Endpoints.VULTISIG_RELAY
            withContext(Dispatchers.IO) {
                startSession(_serverAddress, _sessionID, vault.localPartyID)
            }
            _participantDiscovery?.discoveryParticipants()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Timber.tag("KeysignFlowViewModel").d("onReceive: Mediator service started")
                if (_currentVault == null) {
                    errorMessage.value = "Vault is not set"
                    moveToState(KeysignFlowState.ERROR)
                    return
                }
                // send a request to local mediator server to start the session
                GlobalScope.launch(Dispatchers.IO) {
                    Thread.sleep(1000) // back off a second
                    startSession(_serverAddress, _sessionID, _currentVault!!.localPartyID)
                }
                // kick off discovery
                _participantDiscovery?.discoveryParticipants()
            }
        }
    }

    fun stopService(context: Context) {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stopService: Mediator service stopped")

    }

    private fun startMediatorService(context: Context) {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_EXPORTED)

        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        intent.putExtra("serverName", _serviceName)
        context.startService(intent)
        Timber.tag("KeysignFlowViewModel").d("startMediatorService: Mediator service started")
    }

    private fun startSession(
        serverAddr: String,
        sessionID: String,
        localPartyID: String,
    ) {
        // start the session
        try {
            val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder().url("$serverAddr/$sessionID").post(
                gson.toJson(listOf(localPartyID))
                    .toRequestBody("application/json".toMediaType())
            ).build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_CREATED -> {
                        Timber.tag("KeysignFlowViewModel").d("startSession: Session started")
                    }

                    else -> Timber.tag("KeysignFlowViewModel").d(
                        "startSession: Response code: ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            Timber.tag("KeysignFlowViewModel").e("startSession: ${e.stackTraceToString()}")
        }
    }

    fun addParticipant(participant: String) {
        val currentList = selection.value ?: emptyList()
        if (currentList.contains(participant)) return
        selection.value = currentList + participant
    }

    fun removeParticipant(participant: String) {
        selection.value = selection.value?.minus(participant)
    }

    fun moveToState(nextState: KeysignFlowState) {
        currentState.value = nextState
    }

    fun stopParticipantDiscovery() {
        _participantDiscovery?.stop()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun changeNetworkPromptOption(option: NetworkPromptOption, context: Context) {
        if (networkOption.value == option) return
        when (option) {
            NetworkPromptOption.WIFI, NetworkPromptOption.HOTSPOT -> {
                vultisigRelay.IsRelayEnabled = false
                _serverAddress = "http://127.0.0.1:18080"
                networkOption.value = option
            }

            NetworkPromptOption.CELLULAR -> {
                vultisigRelay.IsRelayEnabled = true
                _serverAddress = Endpoints.VULTISIG_RELAY
                networkOption.value = option
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            updateKeysignPayload(context)
        }
    }

    suspend fun startKeysign() {
        withContext(Dispatchers.IO) {
            try {
                val keygenCommittee = selection.value ?: emptyList()
                val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
                val payload = gson.toJson(keygenCommittee)
                val request = okhttp3.Request.Builder().url("$_serverAddress/start/$_sessionID")
                    .post(payload.toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    if (response.code == HttpURLConnection.HTTP_OK) {
                        Timber.d("Keysign started")
                    } else {
                        Timber.e("Fail to start keysign: Response code: ${response.code}")

                    }
                }
            } catch (e: Exception) {
                Timber.e("Failed to start keysign: ${e.stackTraceToString()}")
            }
        }
    }
}