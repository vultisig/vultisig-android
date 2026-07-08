package com.vultisig.wallet.ui.models.keysign

import android.content.Context
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Endpoints.LOCAL_MEDIATOR_SERVER_URL
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isSecureVault
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.tokenLogoRes
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.services.TransactionStatusServiceManager
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState.Error
import com.vultisig.wallet.ui.models.peer.NetworkOption
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.Keysign.Keysign.TxType.Deposit
import com.vultisig.wallet.ui.navigation.Route.Keysign.Keysign.TxType.Send
import com.vultisig.wallet.ui.navigation.Route.Keysign.Keysign.TxType.Sign
import com.vultisig.wallet.ui.navigation.Route.Keysign.Keysign.TxType.Swap
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asString
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import vultisig.keysign.v1.CustomMessagePayload
import wallet.core.jni.CoinType

internal sealed class KeysignFlowState {
    data object PeerDiscovery : KeysignFlowState()

    data object Keysign : KeysignFlowState()

    data class Error(val errorMessage: UiText) : KeysignFlowState()
}

@Immutable
data class KeysignFlowUiState(
    val vault: Vault = Vault(id = "", name = ""),
    val amount: String = "",
    val toAmount: String = "",
    val toAddress: String = "",
    val isSwap: Boolean = false,
    val qrBitmapPainter: BitmapPainter? = null,
    val isLoading: Boolean = false,
    val enableNotification: Boolean = false,
    val resendCooldownSeconds: Int = 0,
    @param:DrawableRes val srcTokenLogoRes: Int? = null,
    @param:DrawableRes val dstTokenLogoRes: Int? = null,
)

@HiltViewModel
internal class KeysignFlowViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val addressProvider: AddressProvider,
    @ApplicationContext private val context: Context,
    private val navigator: Navigator<Destination>,
    generateServiceName: GenerateServiceName,
    private val transactionStatusServiceManager: TransactionStatusServiceManager,
    private val pushNotificationManager: PushNotificationManager,
    private val snackbarFlow: SnackbarFlow,
    private val keysignViewModelFactory: KeysignViewModel.Factory,
    private val sessionCoordinator: KeysignSessionCoordinator,
    private val participantDiscovery: KeysignParticipantDiscovery,
    private val buildKeysignMessage: BuildKeysignMessageUseCase,
    private val updateSolanaKeysignPayload: UpdateSolanaKeysignPayloadUseCase,
    private val buildKeysignTransactionUiModel: BuildKeysignTransactionUiModelUseCase,
) : ViewModel() {
    private val _sessionID: String = UUID.randomUUID().toString()
    private val _serviceName: String = generateServiceName()
    private var _serverAddress: String = LOCAL_MEDIATOR_SERVER_URL
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex
    private var _currentVault: Vault? = null
    private var _keysignPayload: KeysignPayload? = null
    private var customMessagePayload: CustomMessagePayload? = null
    private val _keysignMessage: MutableStateFlow<String> = MutableStateFlow("")
    private var messagesToSign = emptyList<String>()

    private val _currentState: MutableStateFlow<KeysignFlowState> =
        MutableStateFlow(KeysignFlowState.PeerDiscovery)
    val currentState: StateFlow<KeysignFlowState> = _currentState

    val selection: StateFlow<List<String>>
        get() = participantDiscovery.selection

    val keysignMessage: StateFlow<String> = _keysignMessage

    val participants: StateFlow<List<String>>
        get() = participantDiscovery.participants

    private val _networkOption: MutableStateFlow<NetworkOption> =
        MutableStateFlow(NetworkOption.Internet)
    val networkOption: StateFlow<NetworkOption> = _networkOption

    private val args = savedStateHandle.toRoute<Route.Keysign.Keysign>()
    private val password = args.password
    private val transactionId = args.transactionId

    val isFastSign: Boolean
        get() = !password.isNullOrBlank()

    private val isRelayEnabled: Boolean
        get() = _networkOption.value == NetworkOption.Internet || isFastSign

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded

    private var transactionTypeUiModel: TransactionTypeUiModel? = null
    private var transactionHistoryData = MutableStateFlow<TransactionHistoryData?>(null)

    private val tssKeysignType: TssKeyType
        get() =
            _keysignPayload?.coin?.chain?.TssKeysignType
                ?: customMessagePayload?.chain?.let { raw ->
                    runCatching { Chain.fromRaw(raw).TssKeysignType }.getOrNull()
                }
                ?: TssKeyType.ECDSA

    private var resendCooldownJob: Job? = null
    private var lastNotifiedQrData: String = ""

    private var _keysignViewModel: KeysignViewModel? = null

    val keysignViewModel: KeysignViewModel?
        get() = _keysignViewModel ?: createKeysignViewModel()?.also { _keysignViewModel = it }

    private fun createKeysignViewModel(): KeysignViewModel? {
        val vault =
            _currentVault
                ?: run {
                    Timber.e("Vault is not set when creating KeysignViewModel")
                    return null
                }
        val keysignCommittee = selection.value
        return keysignViewModelFactory.create(
            vault = vault,
            keysignCommittee = keysignCommittee,
            serverUrl = _serverAddress,
            sessionId = _sessionID,
            encryptionKeyHex = _encryptionKeyHex,
            messagesToSign = messagesToSign,
            keyType = tssKeysignType,
            keysignPayload = _keysignPayload,
            customMessagePayload = customMessagePayload,
            transactionTypeUiModel = transactionTypeUiModel,
            isInitiatingDevice = true,
            transactionHistoryData = transactionHistoryData.value,
        )
    }

    private val _uiState = MutableStateFlow(KeysignFlowUiState())
    val uiState: StateFlow<KeysignFlowUiState> = _uiState

    init {
        viewModelScope.launch {
            currentState.collect { state ->
                if (state == KeysignFlowState.Keysign) {
                    startKeysign()
                }
            }
        }
    }

    private var shareVmCollectorsJob: Job? = null

    suspend fun setData(
        shareViewModel: KeysignShareViewModel,
        context: Context,
        txType: Route.Keysign.Keysign.TxType,
    ) {
        _keysignViewModel = null
        try {
            when (txType) {
                Send -> shareViewModel.loadTransaction(transactionId)
                Swap -> shareViewModel.loadSwapTransaction(transactionId)
                Deposit -> shareViewModel.loadDepositTransaction(transactionId)
                Sign -> shareViewModel.loadSignMessageTx(transactionId)
            }
            if (!shareViewModel.hasAllData) {
                moveToState(Error("Keysign information not available".asUiText()))
                return
            }

            val vault = shareViewModel.vault ?: return
            _currentVault = vault
            val keysignPayload = shareViewModel.keysignPayload
            val modifiedKeysignPayload = updateSolanaKeysignPayload(keysignPayload, vault)
            _keysignPayload = modifiedKeysignPayload
            val customMessagePayload = shareViewModel.customMessagePayload
            this.customMessagePayload = customMessagePayload
            messagesToSign =
                when {
                    modifiedKeysignPayload != null ->
                        SigningHelper.getKeysignMessages(
                            payload = modifiedKeysignPayload,
                            vault = vault,
                        )

                    customMessagePayload != null ->
                        SigningHelper.getKeysignMessages(messagePayload = customMessagePayload)

                    else -> error("Payload is null")
                }

            shareVmCollectorsJob?.cancel()
            shareVmCollectorsJob =
                viewModelScope.launch {
                    launch {
                        shareViewModel.amount.collect { amount ->
                            _uiState.update { it.copy(amount = amount) }
                        }
                    }
                    launch {
                        shareViewModel.toAmount.collect { toAmount ->
                            _uiState.update { it.copy(toAmount = toAmount) }
                        }
                    }
                    launch {
                        shareViewModel.qrBitmapPainter.collect { painter ->
                            _uiState.update { it.copy(qrBitmapPainter = painter) }
                        }
                    }
                }

            val srcLogoModel = keysignPayload?.coin?.tokenLogoRes()
            val dstLogoModel = keysignPayload?.swapPayload?.dstToken?.tokenLogoRes()
            _uiState.update {
                it.copy(
                    vault = vault,
                    isSwap = shareViewModel.keysignPayload?.swapPayload != null,
                    toAddress = keysignPayload?.toAddress ?: "",
                    enableNotification = vault.isSecureVault(),
                    srcTokenLogoRes = srcLogoModel,
                    dstTokenLogoRes = dstLogoModel,
                )
            }

            participantDiscovery.setSelection(listOf(vault.localPartyID))
            _serverAddress = Endpoints.VULTISIG_RELAY_URL
            updateKeysignPayload(context)
            updateTransactionUiModel(keysignPayload, customMessagePayload, txType)
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e)
            moveToState(
                Error(e.message?.asUiText() ?: UiText.StringResource(R.string.unknown_error))
            )
        }
    }

    private suspend fun updateKeysignPayload(context: Context) {
        stopParticipantDiscovery()
        val vault =
            _currentVault
                ?: run {
                    moveToState(KeysignFlowState.Error("Vault is not set".asUiText()))
                    return
                }

        if (!isRelayEnabled) {
            sessionCoordinator.startMediatorService(_serviceName) { onMediatorServiceStarted() }
        } else {
            _serverAddress = Endpoints.VULTISIG_RELAY_URL
            withContext(Dispatchers.IO) {
                sessionCoordinator.startSession(
                    _serverAddress,
                    _sessionID,
                    vault.localPartyID,
                    buildJoinRequest(vault),
                )
            }

            participantDiscovery.start(
                viewModelScope,
                _serverAddress,
                _sessionID,
                vault.localPartyID,
            )
        }

        val data =
            buildKeysignMessage(
                keysignPayload = _keysignPayload,
                customMessagePayload = customMessagePayload,
                sessionId = _sessionID,
                serviceName = _serviceName,
                encryptionKeyHex = _encryptionKeyHex,
                serverAddress = _serverAddress,
                useVultisigRelay = isRelayEnabled,
            )

        _keysignMessage.value =
            "https://vultisig.com?type=SignTransaction&resharePrefix=${vault.resharePrefix}&vault=${vault.pubKeyECDSA}&jsonData=" +
                data

        addressProvider.update(_keysignMessage.value)
        if (vault.isSecureVault()) sendNotification()
    }

    /**
     * Invoked once the local mediator service signals it is up: starts the local session (with
     * retries) and kicks off participant discovery. Mirrors the old `serviceStartedReceiver`.
     */
    private fun onMediatorServiceStarted() {
        val vault =
            _currentVault
                ?: run {
                    moveToState(KeysignFlowState.Error("Vault is not set".asUiText()))
                    return
                }
        viewModelScope.launch(Dispatchers.IO) {
            val started =
                sessionCoordinator.startSessionWithRetry(
                    _serverAddress,
                    _sessionID,
                    vault.localPartyID,
                    buildJoinRequest(vault),
                )
            if (started) {
                participantDiscovery.start(
                    viewModelScope,
                    _serverAddress,
                    _sessionID,
                    vault.localPartyID,
                )
            } else {
                participantDiscovery.stop()
                moveToState(KeysignFlowState.Error("Failed to start session".asUiText()))
            }
        }
    }

    private fun buildJoinRequest(vault: Vault): JoinKeysignRequestJson? {
        val password = password
        if (password.isNullOrBlank()) return null
        return JoinKeysignRequestJson(
            publicKeyEcdsa = vault.pubKeyECDSA,
            messages = messagesToSign,
            sessionId = _sessionID,
            hexEncryptionKey = _encryptionKeyHex,
            derivePath = (_keysignPayload?.coin?.coinType ?: CoinType.ETHEREUM).derivationPath(),
            isEcdsa = tssKeysignType == TssKeyType.ECDSA,
            password = password,
            chain = _keysignPayload?.coin?.chain?.raw ?: "",
            mldsa = tssKeysignType == TssKeyType.MLDSA,
        )
    }

    fun sendNotification() {
        if (_uiState.value.resendCooldownSeconds > 0) return
        val currentQrData = _keysignMessage.value
        if (currentQrData == lastNotifiedQrData) return
        viewModelScope.safeLaunch(
            onError = {
                snackbarFlow.showMessage(
                    UiText.StringResource(R.string.push_notifications_failed).asString(context),
                    type = SnackbarType.Error,
                )
            }
        ) {
            val vault = _currentVault ?: return@safeLaunch
            pushNotificationManager.notifyVaultDevices(vault, currentQrData)
            lastNotifiedQrData = currentQrData
            snackbarFlow.showMessage(
                message = context.getString(R.string.push_notifications_sent),
                type = SnackbarType.Success,
            )
            startResendCooldown()
        }
    }

    private fun startResendCooldown() {
        resendCooldownJob?.cancel()
        resendCooldownJob =
            viewModelScope.launch {
                var seconds = 30
                while (seconds > 0) {
                    _uiState.update { it.copy(resendCooldownSeconds = seconds) }
                    delay(1.seconds)
                    seconds--
                }
                _uiState.update { it.copy(resendCooldownSeconds = 0) }
                lastNotifiedQrData = ""
            }
    }

    private fun updateTransactionUiModel(
        keysignPayload: KeysignPayload?,
        customMessagePayload: CustomMessagePayload?,
        txType: Route.Keysign.Keysign.TxType,
    ) {
        if (keysignPayload != null) {
            viewModelScope.safeLaunch {
                val result =
                    buildKeysignTransactionUiModel(keysignPayload, txType, transactionId)
                        ?: return@safeLaunch
                transactionTypeUiModel = result.transactionTypeUiModel
                transactionHistoryData.update { result.transactionHistoryData }
                _isDataLoaded.value = true
            }
        } else {
            transactionTypeUiModel =
                TransactionTypeUiModel.SignMessage(
                    model =
                        SignMessageTransactionUiModel(
                            method = customMessagePayload?.method ?: "",
                            message = customMessagePayload?.message ?: "",
                        )
                )
            _isDataLoaded.value = true
        }
    }

    fun addParticipant(participant: String) {
        participantDiscovery.addParticipant(participant)
    }

    fun handleParticipant(participant: String) {
        participantDiscovery.handleParticipant(participant)
    }

    fun moveToState(nextState: KeysignFlowState) {
        try {
            if (nextState == KeysignFlowState.Keysign) {
                cleanQrAddress()
            }
            _currentState.update { nextState }
        } catch (e: Exception) {
            _isLoading.value = false
            moveToState(
                Error(e.message?.asUiText() ?: UiText.StringResource(R.string.unknown_error))
            )
        }
    }

    fun moveToKeysignState() {
        viewModelScope.launch {
            _isLoading.value = true
            stopParticipantDiscovery()
            moveToState(KeysignFlowState.Keysign)
            _isLoading.value = false
        }
    }

    fun stopParticipantDiscovery() {
        participantDiscovery.stop()
    }

    private fun cleanQrAddress() {
        addressProvider.clean()
    }

    fun changeNetworkPromptOption(option: NetworkOption, context: Context) {
        val resolvedOption =
            if (isFastSign && option == NetworkOption.Local) NetworkOption.Internet else option
        if (_networkOption.value == resolvedOption) return
        _networkOption.value = resolvedOption
        _serverAddress =
            when (resolvedOption) {
                NetworkOption.Local -> {
                    LOCAL_MEDIATOR_SERVER_URL
                }

                NetworkOption.Internet -> {
                    Endpoints.VULTISIG_RELAY_URL
                }
            }

        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "Failed to update keysign payload")
                moveToState(
                    KeysignFlowState.Error(
                        (e.message ?: "Failed to update keysign payload").asUiText()
                    )
                )
            }
        ) {
            withContext(Dispatchers.IO) { updateKeysignPayload(context) }
        }
    }

    private suspend fun startKeysign() {
        withContext(Dispatchers.IO) {
            try {
                sessionCoordinator.startWithCommittee(_serverAddress, _sessionID, selection.value)
                Timber.d("Keysign started")
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e("Failed to start keysign: ${e.stackTraceToString()}")
            }
        }
    }

    override fun onCleared() {
        cleanQrAddress()
        sessionCoordinator.unregisterServiceReceiver()
        sessionCoordinator.stopService()
        super.onCleared()
    }

    fun tryAgain() {
        back()
    }

    fun back() {
        viewModelScope.launch { navigator.navigate(Destination.Back) }
    }

    fun complete() {
        viewModelScope.launch {
            transactionStatusServiceManager.cancelPollingAndRemoveNotification()
            navigator.route(Route.Home(), NavigationOptions(clearBackStack = true))
        }
    }
}
