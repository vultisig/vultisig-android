package com.vultisig.wallet.ui.models.keygen

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.common.UiText
import com.vultisig.wallet.data.api.KeygenApi
import com.vultisig.wallet.data.models.TssAction
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.LastOpenedVaultRepository
import com.vultisig.wallet.data.repositories.VaultDataStoreRepository
import com.vultisig.wallet.data.usecases.SaveVaultUseCase
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessagePuller
import com.vultisig.wallet.data.tss.TssMessenger
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

internal sealed interface KeygenState {
    data object CreatingInstance : KeygenState
    data object KeygenECDSA : KeygenState
    data object KeygenEdDSA : KeygenState
    data object ReshareECDSA : KeygenState
    data object ReshareEdDSA : KeygenState
    data object Success : KeygenState
    data class Error(val errorMessage: UiText,val isThresholdError: Boolean) : KeygenState
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
    @SuppressLint("StaticFieldLeak") private val context: Context,
    private val navigator: Navigator<Destination>,
    private val saveVault: SaveVaultUseCase,
    private val lastOpenedVaultRepository: LastOpenedVaultRepository,
    private val vaultDataStoreRepository: VaultDataStoreRepository,
    private val keygenApi: KeygenApi,
    internal val isReshareMode: Boolean
) : ViewModel(){
    private var tssInstance: ServiceImpl? = null
    private val tssMessenger: TssMessenger =
        TssMessenger(serverAddress, sessionId, encryptionKeyHex)
    private val localStateAccessor: tss.LocalStateAccessor = LocalStateAccessor(vault)
    val currentState: MutableStateFlow<KeygenState> = MutableStateFlow(KeygenState.CreatingInstance)
    private var _messagePuller: TssMessagePuller? = null

    init {
        viewModelScope.launch {
            collectCurrentState()
        }
    }

    private suspend fun collectCurrentState() {
        currentState.collect { state ->
            when (state) {
                is KeygenState.Error -> {
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

        try {
            withContext(Dispatchers.IO) {
                createInstance()
            }
            this.tssInstance?.let {
                keygenWithRetry(it, 1)
            }
            this.vault.signers = keygenCommittee
            currentState.value = KeygenState.Success
            this._messagePuller?.stop()
        } catch (e: Exception) {
            Timber.tag("GeneratingKeyViewModel").d("generateKey error: %s", e.stackTraceToString())
            val errorMessage = UiText.DynamicString(e.message ?: "Unknown error")
            val isThresholdError = checkIsThresholdError(e)
            currentState.value = KeygenState.Error(
                if (isThresholdError)
                    UiText.StringResource(R.string.threshold_error) else errorMessage,
                isThresholdError
            )
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
            withContext(Dispatchers.IO) {
                keygenApi.markLocalPartyComplete(serverAddress, sessionId, listOf(vault.localPartyID))
                Timber.d("Local party ${vault.localPartyID} marked as complete")
                var counter = 0
                while (counter < 60){
                    val serverCompletedParties = keygenApi.getCompletedParties(serverAddress, sessionId)
                    if (!serverCompletedParties.containsAll(keygenCommittee)) break
                    delay(1000)
                    counter++
                }
                Timber.d("All parties have completed the key generation process")

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

    private suspend fun saveVault(context: Context) {

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
            opts = NavigationOptions(popUpTo = Destination.Home().route)
        )
    }

    fun stopService(context: Context) {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stop MediatorService: Mediator service stopped")

    }

    private fun checkIsThresholdError(errorMessage: Exception) =
         errorMessage.message?.contains("threshold") == true
}