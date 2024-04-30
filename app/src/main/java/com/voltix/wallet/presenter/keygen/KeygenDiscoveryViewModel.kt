package com.voltix.wallet.presenter.keygen

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.voltix.wallet.common.Utils
import com.voltix.wallet.common.VoltixRelay
import com.voltix.wallet.models.TssAction
import com.voltix.wallet.models.Vault
import dagger.hilt.android.lifecycle.HiltViewModel
import java.security.SecureRandom
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

@HiltViewModel
class KeygenDiscoveryViewModel @Inject constructor(
    private val voltixRelay: VoltixRelay,
) : ViewModel() {
    private val relayEnabled: LiveData<Boolean> = MutableLiveData(voltixRelay.IsRelayEnabled)
    private val sessionID: String = UUID.randomUUID().toString() // generate a random UUID
    private val serviceName: String = "VoltixApp-${Random.nextInt(1, 1000)}"
    private val serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var participantDiscovery: ParticipantDiscovery =
        ParticipantDiscovery(sessionID, serviceName, serverAddress)
    private var action: TssAction = TssAction.KEYGEN
    private var vault: Vault = Vault("New Vault")
    val selection = MutableLiveData<List<String>>()

    fun setData(action: TssAction,vault: Vault) {
        this.action = action
        this.vault = vault
        if(this.vault.HexChainCode.isEmpty()){
            val secureRandom = SecureRandom()
            val randomBytes = ByteArray(32)
            secureRandom.nextBytes(randomBytes)
            this.vault.HexChainCode = randomBytes.joinToString("") { "%02x".format(it) }
        }
        if (this.vault.LocalPartyID.isEmpty()){
            this.vault.LocalPartyID = Utils.deviceName
        }
    }

    fun startDiscovery() {
        // start discovery
        participantDiscovery.discoveryParticipants()
    }
    fun addParticipant(participant: String) {
        val currentList = selection.value ?: emptyList()
        if(currentList.contains(participant)) return
        selection.value = currentList + participant
    }
    fun removeParticipant(participant: String) {
        selection.value = selection.value?.minus(participant)
    }
}