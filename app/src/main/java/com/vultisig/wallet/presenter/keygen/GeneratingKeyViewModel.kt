package com.vultisig.wallet.presenter.keygen

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.mediator.MediatorService
import com.vultisig.wallet.models.TssAction
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.tss.LocalStateAccessor
import com.vultisig.wallet.tss.TssKeyType
import com.vultisig.wallet.tss.TssMessagePuller
import com.vultisig.wallet.tss.TssMessenger
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.ServiceImpl
import tss.Tss
import kotlin.time.Duration.Companion.seconds

enum class KeygenState {
    CreatingInstance, KeygenECDSA, KeygenEdDSA, ReshareECDSA, ReshareEdDSA, Success, ERROR
}

internal class GeneratingKeyViewModel(
    private val vault: Vault,
    private val action: TssAction,
    private val keygenCommittee: List<String>,
    private val oldCommittee: List<String>,
    private val serverAddress: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
    private val oldResharePrefix: String,
    private val gson: Gson,
    private val context: Context,
    private val navigator: Navigator<Destination>,
    private val saveVault: SaveVaultUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
) : ViewModel(){
    private var tssInstance: ServiceImpl? = null
    private val tssMessenger: TssMessenger =
        TssMessenger(serverAddress, sessionId, encryptionKeyHex)
    private val localStateAccessor: LocalStateAccessor = LocalStateAccessor(vault)
    val currentState: MutableStateFlow<KeygenState> = MutableStateFlow(KeygenState.CreatingInstance)
    val errorMessage: MutableState<String> = mutableStateOf("")
    private var _messagePuller: TssMessagePuller? = null

    init {
        viewModelScope.launch {
            collectCurrentState()
        }
    }

    private suspend fun collectCurrentState() {
        currentState.collect { state ->
            when (state) {
                KeygenState.ERROR -> {
                    stopService(context.applicationContext)
                }

                KeygenState.Success -> {
                    saveVault(context.applicationContext)
                }

                else -> Unit
            }
        }
    }

    suspend fun generateKey() {
        currentState.value = KeygenState.CreatingInstance
        withContext(Dispatchers.IO) {
            createInstance()
        }

        try {
            this.tssInstance?.let {
                keygenWithRetry(it, 1)
            }
            this.vault.signers = keygenCommittee
            currentState.value = KeygenState.Success
            this._messagePuller?.stop()
        } catch (e: Exception) {
            Timber.tag("GeneratingKeyViewModel").d("generateKey error: %s", e.stackTraceToString())
            errorMessage.value = e.message ?: "Unknown error"
            currentState.value = KeygenState.ERROR
        }

    }

    private suspend fun keygenWithRetry(service: ServiceImpl, attempt: Int = 1) {
        try {
            _messagePuller = TssMessagePuller(
                service, this.encryptionKeyHex, serverAddress, vault.localPartyID, sessionId
            )
            _messagePuller?.pullMessages(null)
            when (this.action) {
                TssAction.KEYGEN -> {
                    // generate ECDSA
                    currentState.value = KeygenState.KeygenECDSA
                    val keygenRequest = tss.KeygenRequest()
                    keygenRequest.localPartyID = vault.localPartyID
                    keygenRequest.allParties = keygenCommittee.joinToString(",")
                    keygenRequest.chainCodeHex = vault.hexChainCode
                    val ecdsaResp = tssKeygen(service, keygenRequest, TssKeyType.ECDSA)
                    vault.pubKeyECDSA = ecdsaResp.pubKey
                    delay(1.seconds) // backoff for 1 second
                    currentState.value = KeygenState.KeygenEdDSA
                    val eddsaResp = tssKeygen(service, keygenRequest, TssKeyType.EDDSA)
                    vault.pubKeyEDDSA = eddsaResp.pubKey
                }

                TssAction.ReShare -> {
                    currentState.value = KeygenState.ReshareECDSA
                    val reshareRequest = tss.ReshareRequest()
                    reshareRequest.localPartyID = vault.localPartyID
                    reshareRequest.pubKey = vault.pubKeyECDSA
                    reshareRequest.oldParties = oldCommittee.joinToString(",")
                    reshareRequest.newParties = keygenCommittee.joinToString(",")
                    reshareRequest.resharePrefix =
                        vault.resharePrefix.ifEmpty { oldResharePrefix }
                    reshareRequest.chainCodeHex = vault.hexChainCode
                    val ecdsaResp = tssReshare(service, reshareRequest, TssKeyType.ECDSA)
                    currentState.value = KeygenState.ReshareEdDSA
                    delay(1.seconds) // backoff for 1 second
                    reshareRequest.pubKey = vault.pubKeyEDDSA
                    reshareRequest.newResharePrefix = ecdsaResp.resharePrefix
                    val eddsaResp = tssReshare(service, reshareRequest, TssKeyType.EDDSA)
                    vault.pubKeyEDDSA = eddsaResp.pubKey
                    vault.pubKeyECDSA = ecdsaResp.pubKey
                    vault.resharePrefix = ecdsaResp.resharePrefix
                }
            }
            // here is the keygen process is done
            val keygenVerifier = KeygenVerifier(
                this.serverAddress,
                this.sessionId,
                vault.localPartyID,
                this.keygenCommittee, gson = gson,
            )
            withContext(Dispatchers.IO) {
                keygenVerifier.markLocalPartyComplete()
                if (!keygenVerifier.checkCompletedParties()) {
                    throw Exception("another party failed to complete the keygen process")
                }
            }
        } catch (e: Exception) {
            this._messagePuller?.stop()
            Timber.tag("GeneratingKeyViewModel")
                .e("attempt $attempt keygenWithRetry: ${e.stackTraceToString()}")
            if (attempt < 3) {
                keygenWithRetry(service, attempt + 1)
            } else {
                throw e
            }
        }
    }

    private fun createInstance() {
        // this will take a while
        this.tssInstance = Tss.newService(this.tssMessenger, this.localStateAccessor, true)
    }

    private suspend fun tssKeygen(
        service: ServiceImpl,
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
        service: ServiceImpl,
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

    suspend fun saveVault(context: Context) {

        saveVault(
            this@GeneratingKeyViewModel.vault,
            this@GeneratingKeyViewModel.action == TssAction.ReShare
        )
        vaultDataStoreRepository.setBackupStatus(vaultId = vault.id, false)

        delay(2.seconds)

        stopService(context)

        lastOpenedVaultRepository.setLastOpenedVaultId(vault.id)

        navigator.navigate(
            Destination.BackupSuggestion(
                vaultId = vault.id
            ),
            opts = NavigationOptions(popUpTo = Destination.Home.staticRoute)
        )
    }

    fun stopService(context: Context) {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stop MediatorService: Mediator service stopped")

    }
}