package com.voltix.wallet.presenter.keygen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.voltix.wallet.common.Utils
import com.voltix.wallet.common.VoltixRelay
import com.voltix.wallet.mediator.MediatorService
import com.voltix.wallet.models.KeygenMessage
import com.voltix.wallet.models.PeerDiscoveryPayload
import com.voltix.wallet.models.ReshareMessage
import com.voltix.wallet.models.TssAction
import com.voltix.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class KeygenDiscoveryViewModel @Inject constructor(
    private val voltixRelay: VoltixRelay,
) : ViewModel() {
    private val sessionID: String = UUID.randomUUID().toString() // generate a random UUID
    val serviceName: String = "VoltixApp-${Random.nextInt(1, 1000)}"
    private var serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var participantDiscovery: ParticipantDiscovery? = null
    private var action: TssAction = TssAction.KEYGEN
    private var vault: Vault = Vault("New Vault")
    val selection = MutableLiveData<List<String>>()
    val keygenPayloadState: State<String>
        get() = _keygenPayload
    private val _keygenPayload: MutableState<String> = mutableStateOf("")
    val participants: MutableLiveData<List<String>>
        get() = participantDiscovery?.participants ?: MutableLiveData(listOf())

    fun setData(action: TssAction, vault: Vault, context: Context) {
        this.action = action
        this.vault = vault
        if (this.vault.HexChainCode.isEmpty()) {
            val secureRandom = SecureRandom()
            val randomBytes = ByteArray(32)
            secureRandom.nextBytes(randomBytes)
            this.vault.HexChainCode = randomBytes.joinToString("") { "%02x".format(it) }
        }
        if (this.vault.LocalPartyID.isEmpty()) {
            this.vault.LocalPartyID = Utils.deviceName
        }
        this.selection.value = listOf(this.vault.LocalPartyID)
        this.participantDiscovery =
            ParticipantDiscovery(serverAddress, sessionID, this.vault.LocalPartyID)
        when (action) {
            TssAction.KEYGEN -> {
                _keygenPayload.value = PeerDiscoveryPayload.Keygen(
                    keygenMessage = KeygenMessage(
                        sessionID = sessionID,
                        hexChainCode = vault.HexChainCode,
                        serviceName = serviceName,
                        encryptionKeyHex = Utils.encryptionKeyHex,
                        useVoltixRelay = voltixRelay.IsRelayEnabled
                    )
                ).toJson()
            }

            TssAction.ReShare -> {
                _keygenPayload.value = PeerDiscoveryPayload.Reshare(
                    reshareMessage = ReshareMessage(
                        sessionID = sessionID,
                        hexChainCode = vault.HexChainCode,
                        serviceName = serviceName,
                        pubKeyECDSA = vault.PubKeyECDSA,
                        oldParties = vault.signers,
                        encryptionKeyHex = Utils.encryptionKeyHex,
                        useVoltixRelay = voltixRelay.IsRelayEnabled
                    )
                ).toJson()
            }
        }
        if (!voltixRelay.IsRelayEnabled)
        // when relay is disabled, start the mediator service
            startMediatorService(context)
        else {
            // start the session
            startSession(serverAddress, sessionID, vault.LocalPartyID)
            // kick off discovery
            participantDiscovery?.discoveryParticipants()
        }
    }

    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("KeygenDiscoveryViewModel", "onReceive: ${intent.action}")
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Log.d("KeygenDiscoveryViewModel", "onReceive: Mediator service started")
                // send a request to local mediator server to start the session
                startSession(serverAddress, sessionID, vault.LocalPartyID)

                // kick off discovery
                participantDiscovery?.discoveryParticipants()
            }
        }
    }

    private fun startMediatorService(context: Context) {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_NOT_EXPORTED)

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
            val url = "$serverAddr/$sessionID"
            val requestURL = URL(url)
            val conn = requestURL.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            val payload = listOf(localPartyID)
            Gson().toJson(payload).also {
                conn.outputStream.write(it.toByteArray())
            }
            val responseCode = conn.responseCode
            Log.d("KeygenDiscoveryViewModel", "startSession: Response code: $responseCode")
            conn.disconnect()
        } catch (e: Exception) {
            Log.e("KeygenDiscoveryViewModel", "startSession: ${e.message}")
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
}