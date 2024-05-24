package com.vultisig.wallet.presenter.keygen

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.vultisig.wallet.R
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.common.vultisigRelay
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.mediator.MediatorService
import com.vultisig.wallet.models.KeygenMessage
import com.vultisig.wallet.models.PeerDiscoveryPayload
import com.vultisig.wallet.models.ReshareMessage
import com.vultisig.wallet.models.TssAction
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
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
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

enum class KeygenFlowState {
    PEER_DISCOVERY, DEVICE_CONFIRMATION, KEYGEN, ERROR, SUCCESS
}

@HiltViewModel
internal class KeygenFlowViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val vultisigRelay: vultisigRelay,
    private val gson: Gson,
    private val navBackStackEntry: SavedStateHandle,
    @ApplicationContext private val context: Context
) : ViewModel() {
    private val sessionID: String = UUID.randomUUID().toString() // generate a random UUID
    private val serviceName: String = "vultisigApp-${Random.nextInt(1, 1000)}"
    private var serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var participantDiscovery: ParticipantDiscovery? = null
    private var action: TssAction = TssAction.KEYGEN
    private var vault: Vault = Vault(id = UUID.randomUUID().toString(),"New Vault")
    private val _keygenPayload: MutableState<String> = mutableStateOf("")
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex
    private var _oldResharePrefix: String = ""

    var currentState: MutableState<KeygenFlowState> = mutableStateOf(KeygenFlowState.PEER_DISCOVERY)
    var errorMessage: MutableState<String> = mutableStateOf("")

    var vaultId = navBackStackEntry.get<String>(Screen.KeygenFlow.ARG_VAULT_NAME)?:""
    var initVault : Vault? = null
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
            vault.signers.filter { (selection.value ?: emptyList()).contains(it) },
            serverAddress,
            sessionID,
            _encryptionKeyHex,
            _oldResharePrefix,
            gson,
            vaultRepository = vaultRepository,
        )

    suspend fun setData(vaultId: String, context: Context) {
        // start mediator server

        val allVaults = vaultRepository.getAll()

        val vault = if (vaultId == Destination.KeygenFlow.DEFAULT_NEW_VAULT) {
            var newVaultName = ""
            var idx = 1
            while (true) {
                newVaultName = "New vault ${allVaults.size + idx}"
                if (allVaults.find { it.name == newVaultName } == null) {
                    break
                }
                idx++
            }
            Vault(id = UUID.randomUUID().toString(), newVaultName)
        } else {
            vaultRepository.get(vaultId) ?: Vault(id = UUID.randomUUID().toString(), vaultId)
        }

        val action = if (vault.pubKeyECDSA.isEmpty())
            TssAction.KEYGEN
        else
            TssAction.ReShare

        if (vultisigRelay.IsRelayEnabled) {
            serverAddress = Endpoints.VULTISIG_RELAY
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
        _oldResharePrefix = this.vault.resharePrefix
        updateKeygenPayload(context)
    }

    private suspend fun updateKeygenPayload(context: Context) {
        // stop participant discovery
        stopParticipantDiscovery()
        this.participantDiscovery =
            ParticipantDiscovery(serverAddress, sessionID, this.vault.localPartyID, gson)
        when (action) {
            TssAction.KEYGEN -> {
                _keygenPayload.value =
                    "vultisig://vultisig.com?type=NewVault&tssType=Keygen&jsonData=" + PeerDiscoveryPayload.Keygen(
                        keygenMessage = KeygenMessage(
                            sessionID = sessionID,
                            hexChainCode = vault.hexChainCode,
                            serviceName = serviceName,
                            encryptionKeyHex = this._encryptionKeyHex,
                            useVultisigRelay = vultisigRelay.IsRelayEnabled,
                            vaultName = this.vault.name,
                        )
                    ).toJson(gson)
            }

            TssAction.ReShare -> {
                _keygenPayload.value =
                    "vultisig://vultisig.com?type=NewVault&tssType=Reshare&jsonData=" + PeerDiscoveryPayload.Reshare(
                        reshareMessage = ReshareMessage(
                            sessionID = sessionID,
                            hexChainCode = vault.hexChainCode,
                            serviceName = serviceName,
                            pubKeyECDSA = vault.pubKeyECDSA,
                            oldParties = vault.signers,
                            encryptionKeyHex = this._encryptionKeyHex,
                            useVultisigRelay = vultisigRelay.IsRelayEnabled,
                            oldResharePrefix = vault.resharePrefix,
                        )
                    ).toJson(gson)
            }
        }

        if (!vultisigRelay.IsRelayEnabled)
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

    fun stopParticipantDiscovery() {
        participantDiscovery?.stop()
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Timber.d("onReceive: Mediator service started")
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
        Timber.d("startMediatorService: Mediator service started")
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
                        Timber.d("startSession: Session started")
                    }

                    else -> Timber.d("startSession: Response code: " + response.code)
                }
            }
        } catch (e: Exception) {
            Timber.e("startSession: ${e.stackTraceToString()}")
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
            val payload = gson.toJson(keygenCommittee)
            val request = okhttp3.Request.Builder().url("$serverAddress/start/$sessionID")
                .post(payload.toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (response.code == HttpURLConnection.HTTP_OK) {
                    Timber.tag("KeygenDiscoveryViewModel").d("startKeygen: Keygen started")
                } else {
                    Timber.tag("KeygenDiscoveryViewModel")
                        .e("startKeygen: Response code: %s", response.code)
                }
            }
        } catch (e: Exception) {
            Timber.tag("KeygenDiscoveryViewModel").e("startKeygen: %s", e.stackTraceToString())
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun changeNetworkPromptOption(option: NetworkPromptOption, context: Context) {
        if (networkOption.value == option) return
        when (option) {
            NetworkPromptOption.WIFI, NetworkPromptOption.HOTSPOT -> {
                vultisigRelay.IsRelayEnabled = false
                serverAddress = "http://127.0.0.1:18080"
                networkOption.value = option
            }

            NetworkPromptOption.CELLULAR -> {
                vultisigRelay.IsRelayEnabled = true
                serverAddress = Endpoints.VULTISIG_RELAY
                networkOption.value = option
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            updateKeygenPayload(context)
        }
    }
}