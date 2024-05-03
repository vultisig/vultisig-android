package com.voltix.wallet.presenter.keygen

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import com.voltix.wallet.data.on_board.db.VaultDB
import com.voltix.wallet.mediator.MediatorService
import com.voltix.wallet.models.TssAction
import com.voltix.wallet.models.Vault
import com.voltix.wallet.tss.LocalStateAccessor
import com.voltix.wallet.tss.TssKeyType
import com.voltix.wallet.tss.TssMessagePuller
import com.voltix.wallet.tss.TssMessenger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tss.ServiceImpl
import tss.Tss

enum class KeygenState {
    CreatingInstance,
    KeygenECDSA,
    KeygenEdDSA,
    ReshareECDSA,
    ReshareEdDSA,
    Success,
    ERROR
}

class GeneratingKeyViewModel(
    private val vault: Vault,
    private val action: TssAction,
    private val keygenCommittee: List<String>,
    private val oldCommittee: List<String>,
    private val serverAddress: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
) {
    private var tssInstance: tss.ServiceImpl? = null
    private val tssMessenger: TssMessenger =
        TssMessenger(serverAddress, sessionId, encryptionKeyHex)
    private val localStateAccessor: LocalStateAccessor = LocalStateAccessor(vault)
    val currentState: MutableState<KeygenState> = mutableStateOf(KeygenState.CreatingInstance)
    val errorMessage: MutableState<String> = mutableStateOf("")
    private var _messagePuller: TssMessagePuller? = null
    suspend fun generateKey() {
        currentState.value = KeygenState.CreatingInstance
        withContext(Dispatchers.IO) {
            createInstance()
        }

        try {
            this.tssInstance?.let {
                keygenWithRetry(it, 1)
            }
            currentState.value = KeygenState.Success
            this._messagePuller?.stop()
        } catch (e: Exception) {
            Log.d("GeneratingKeyViewModel", "generateKey error: ${e.stackTraceToString()}")
            errorMessage.value = e.message ?: "Unknown error"
            currentState.value = KeygenState.ERROR
        }

    }

    private suspend fun keygenWithRetry(service: ServiceImpl, attempt: Int = 1) {
        try {
            _messagePuller = TssMessagePuller(
                service,
                this.encryptionKeyHex,
                serverAddress,
                vault.LocalPartyID,
                sessionId
            )
            _messagePuller?.pullMessages(null)
            when (this.action) {
                TssAction.KEYGEN -> {
                    // generate ECDSA
                    currentState.value = KeygenState.KeygenECDSA
                    val keygenRequest = tss.KeygenRequest()
                    keygenRequest.localPartyID = vault.LocalPartyID
                    keygenRequest.allParties = keygenCommittee.joinToString(",")
                    keygenRequest.chainCodeHex = vault.HexChainCode
                    val ecdsaResp = tssKeygen(service, keygenRequest, TssKeyType.ECDSA)
                    vault.PubKeyECDSA = ecdsaResp.pubKey
                    Thread.sleep(1000) // backoff for 1 second
                    currentState.value = KeygenState.KeygenEdDSA
                    val eddsaResp = tssKeygen(service, keygenRequest, TssKeyType.EDDSA)
                    vault.PubKeyEDDSA = eddsaResp.pubKey
                }

                TssAction.ReShare -> {
                    currentState.value = KeygenState.ReshareECDSA
                    val reshareRequest = tss.ReshareRequest()
                    reshareRequest.localPartyID = vault.LocalPartyID
                    reshareRequest.pubKey = vault.PubKeyECDSA
                    reshareRequest.oldParties = oldCommittee.joinToString(",")
                    reshareRequest.newParties = keygenCommittee.joinToString(",")
                    reshareRequest.resharePrefix = vault.ResharePrefix
                    reshareRequest.chainCodeHex = vault.HexChainCode
                    val ecdsaResp = tssReshare(service, reshareRequest, TssKeyType.ECDSA)
                    vault.PubKeyECDSA = ecdsaResp.pubKey
                    vault.ResharePrefix = ecdsaResp.resharePrefix
                    currentState.value = KeygenState.ReshareEdDSA
                    Thread.sleep(1000) // backoff for 1 second
                    reshareRequest.pubKey = vault.PubKeyEDDSA
                    reshareRequest.newResharePrefix = vault.ResharePrefix
                    val eddsaResp = tssReshare(service, reshareRequest, TssKeyType.EDDSA)
                    vault.PubKeyEDDSA = eddsaResp.pubKey
                }
            }
            // here is the keygen process is done
            val keygenVerifier = KeygenVerifier(
                this.serverAddress,
                this.sessionId,
                vault.LocalPartyID,
                this.keygenCommittee
            )
            withContext(Dispatchers.IO) {
                keygenVerifier.markLocalPartyComplete()
                if (!keygenVerifier.checkCompletedParties()){
                    throw Exception("another party failed to complete the keygen process")
                }
            }
        } catch (e: Exception) {
            this._messagePuller?.stop()
            Log.e(
                "GeneratingKeyViewModel",
                "attempt $attempt,keygenWithRetry: ${e.stackTraceToString()}"
            )
            if (attempt < 3) {
                keygenWithRetry(service, attempt + 1)
            } else {
                throw e
            }
        }
    }

    private suspend fun createInstance() {
        // this will take a while
        this.tssInstance = Tss.newService(this.tssMessenger, this.localStateAccessor, true)
    }

    private suspend fun tssKeygen(
        service: tss.ServiceImpl,
        keygenRequest: tss.KeygenRequest,
        tssKeyType: TssKeyType,
    ): tss.KeygenResponse {
        return withContext(Dispatchers.IO) {
            when (tssKeyType) {
                TssKeyType.ECDSA -> {
                    return@withContext service.keygenECDSA(keygenRequest)
                }

                TssKeyType.EDDSA -> {
                    return@withContext service.keygenEdDSA(keygenRequest)
                }
            }
        }
    }

    private suspend fun tssReshare(
        service: tss.ServiceImpl,
        reshareRequest: tss.ReshareRequest,
        tssKeyType: TssKeyType,
    ): tss.ReshareResponse {
        return withContext(Dispatchers.IO) {
            when (tssKeyType) {
                TssKeyType.ECDSA -> {
                    return@withContext service.reshareECDSA(reshareRequest)
                }

                TssKeyType.EDDSA -> {
                    return@withContext service.resharingEdDSA(reshareRequest)
                }
            }
        }
    }

    fun saveVault(context: Context) {
        val vaultDB = VaultDB(context)
        vaultDB.upsert(this.vault)
        Log.d("GeneratingKeyViewModel", "saveVault: success,name:${vault.name}")
    }
    fun stopService(context: Context){
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Log.d("KeygenDiscoveryViewModel", "startMediatorService: Mediator service started")

    }
}