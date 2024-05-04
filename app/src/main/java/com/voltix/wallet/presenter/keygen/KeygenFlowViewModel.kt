package com.voltix.wallet.presenter.keygen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.voltix.wallet.common.Endpoints
import com.voltix.wallet.common.Utils
import com.voltix.wallet.common.VoltixRelay
import com.voltix.wallet.mediator.MediatorService
import com.voltix.wallet.models.KeygenMessage
import com.voltix.wallet.models.PeerDiscoveryPayload
import com.voltix.wallet.models.ReshareMessage
import com.voltix.wallet.models.TssAction
import com.voltix.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.HttpURLConnection
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

enum class KeygenFlowState {
    PEER_DISCOVERY,
    DEVICE_CONFIRMATION,
    KEYGEN,
    ERROR,
    SUCCESS
}

@HiltViewModel
class KeygenFlowViewModel @Inject constructor(
    private val voltixRelay: VoltixRelay,
) : ViewModel() {
    private val sessionID: String = UUID.randomUUID().toString() // generate a random UUID
    private val serviceName: String = "VoltixApp-${Random.nextInt(1, 1000)}"
    private var serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var participantDiscovery: ParticipantDiscovery? = null
    private var action: TssAction = TssAction.KEYGEN
    private var vault: Vault = Vault("New Vault")
    private val _keygenPayload: MutableState<String> = mutableStateOf("")
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex


    var currentState: MutableState<KeygenFlowState> = mutableStateOf(KeygenFlowState.PEER_DISCOVERY)
    var errorMessage: MutableState<String> = mutableStateOf("")

    val selection = MutableLiveData<List<String>>()
    val keygenPayloadState: MutableState<String>
        get() = _keygenPayload

    val localPartyID: String
        get() = vault.localPartyID
    val participants: MutableLiveData<List<String>>
        get() = participantDiscovery?.participants ?: MutableLiveData(listOf())

    val networkOption: MutableState<NetworkPromptOption> = mutableStateOf(NetworkPromptOption.WIFI)
    val generatingKeyViewModel: GeneratingKeyViewModel
        get() = GeneratingKeyViewModel(
            vault,
            this.action,
            selection.value ?: emptyList(),
            vault.signers,
            serverAddress,
            sessionID,
            _encryptionKeyHex
        )

    suspend fun setData(action: TssAction, vault: Vault, context: Context) {
        if (voltixRelay.IsRelayEnabled) {
            serverAddress = Endpoints.VOLTIX_RELAY
            networkOption.value = NetworkPromptOption.CELLULAR
        }
        this.action = action
        this.vault = vault
        if (this.vault.hexChainCode.isEmpty()) {
            val secureRandom = SecureRandom()
            val randomBytes = ByteArray(32)
            secureRandom.nextBytes(randomBytes)
            this.vault.hexChainCode = randomBytes.joinToString("") { "%02x".format(it) }
        }
        if (this.vault.localPartyID.isEmpty()) {
            this.vault.localPartyID = Utils.deviceName
        }
        this.selection.value = listOf(this.vault.localPartyID)
        updateKeygenPayload(context)
    }

    private suspend fun updateKeygenPayload(context: Context) {
        // stop participant discovery
        stopParticipantDiscovery()
        this.participantDiscovery =
            ParticipantDiscovery(serverAddress, sessionID, this.vault.localPartyID)
        when (action) {
            TssAction.KEYGEN -> {
                _keygenPayload.value = PeerDiscoveryPayload.Keygen(
                    keygenMessage = KeygenMessage(
                        sessionID = sessionID,
                        hexChainCode = vault.hexChainCode,
                        serviceName = serviceName,
                        encryptionKeyHex = this._encryptionKeyHex,
                        useVoltixRelay = voltixRelay.IsRelayEnabled
                    )
                ).toJson()
            }

            TssAction.ReShare -> {
                _keygenPayload.value = PeerDiscoveryPayload.Reshare(
                    reshareMessage = ReshareMessage(
                        sessionID = sessionID,
                        hexChainCode = vault.hexChainCode,
                        serviceName = serviceName,
                        pubKeyECDSA = vault.pubKeyECDSA,
                        oldParties = vault.signers,
                        encryptionKeyHex = this._encryptionKeyHex,
                        useVoltixRelay = voltixRelay.IsRelayEnabled
                    )
                ).toJson()
            }
        }

        if (!voltixRelay.IsRelayEnabled)
        // when relay is disabled, start the mediator service
            startMediatorService(context)
        else {
            serverAddress = Endpoints.VOLTIX_RELAY
            // start the session
            withContext(Dispatchers.IO) {
                startSession(serverAddress, sessionID, vault.localPartyID)
            }
            // kick off discovery
            participantDiscovery?.discoveryParticipants()
        }
    }

    fun stopParticipantDiscovery() {
        participantDiscovery?.stop()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Log.d("KeygenDiscoveryViewModel", "onReceive: Mediator service started")
                // send a request to local mediator server to start the session
                GlobalScope.launch(Dispatchers.IO) {
                    Thread.sleep(1000) // back off a second
                    startSession(serverAddress, sessionID, vault.localPartyID)
                }
                // kick off discovery
                participantDiscovery?.discoveryParticipants()
            }
        }
    }

    private fun startMediatorService(context: Context) {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_EXPORTED)

        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        intent.putExtra("serverName", serviceName)
        context.startService(intent)
        Log.d("KeygenDiscoveryViewModel", "startMediatorService: Mediator service started")
    }

    private fun startSession(
        serverAddr: String,
        sessionID: String,
        localPartyID: String,
    ) {
        // start the session
        try {
            val client = OkHttpClient.Builder()
                .retryOnConnectionFailure(true)
                .build()
            val request = okhttp3.Request.Builder()
                .url("$serverAddr/$sessionID")
                .post(
                    Gson().toJson(listOf(localPartyID))
                        .toRequestBody("application/json".toMediaType())
                )
                .build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_CREATED -> {
                        Log.d("KeygenDiscoveryViewModel", "startSession: Session started")
                    }

                    else ->
                        Log.d(
                            "KeygenDiscoveryViewModel",
                            "startSession: Response code: ${response.code}"
                        )
                }
            }
        } catch (e: Exception) {
            Log.e("KeygenDiscoveryViewModel", "startSession: ${e.stackTraceToString()}")
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

    fun moveToState(nextState: KeygenFlowState) {
        currentState.value = nextState
    }

    fun startKeygen() {
        try {
            val keygenCommittee = selection.value ?: emptyList()
            val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
            val payload = Gson().toJson(keygenCommittee)
            val request = okhttp3.Request
                .Builder()
                .url("$serverAddress/start/$sessionID")
                .post(payload.toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (response.code == HttpURLConnection.HTTP_OK) {
                    Log.d("KeygenDiscoveryViewModel", "startKeygen: Keygen started")
                } else {
                    Log.e(
                        "KeygenDiscoveryViewModel",
                        "startKeygen: Response code: ${response.code}"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e("KeygenDiscoveryViewModel", "startKeygen: ${e.stackTraceToString()}")
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun changeNetworkPromptOption(option: NetworkPromptOption, context: Context) {
        if (networkOption.value == option) return
        when (option) {
            NetworkPromptOption.WIFI, NetworkPromptOption.HOTSPOT -> {
                voltixRelay.IsRelayEnabled = false
                serverAddress = "http://127.0.0.1:18080"
                networkOption.value = option
            }

            NetworkPromptOption.CELLULAR -> {
                voltixRelay.IsRelayEnabled = true
                serverAddress = Endpoints.VOLTIX_RELAY
                networkOption.value = option
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            updateKeygenPayload(context)
        }
    }
}