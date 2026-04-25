@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.RouterApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Endpoints.LOCAL_MEDIATOR_SERVER_URL
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.mappers.PayloadToProtoMapper
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isSecureVault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.tokenLogoRes
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.services.PushNotificationManager
import com.vultisig.wallet.data.services.TransactionStatusServiceManager
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.data.usecases.tss.PullTssMessagesUseCase
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfigurationProvider
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.v2.snackbar.SnackbarType
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.models.keysign.KeysignFlowState.Error
import com.vultisig.wallet.ui.models.mappers.DepositTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
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
import io.ktor.util.encodeBase64
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
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import vultisig.keysign.v1.CustomMessagePayload
import vultisig.keysign.v1.TransactionType
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
    private val protoBuf: ProtoBuf,
    private val thorChainApi: ThorChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val addressProvider: AddressProvider,
    @ApplicationContext private val context: Context,
    private val compressQr: CompressQrUseCase,
    private val navigator: Navigator<Destination>,
    private val vultiSignerRepository: VultiSignerRepository,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
    private val transactionRepository: TransactionRepository,
    private val depositTransactionRepository: DepositTransactionRepository,
    private val swapTransactionRepository: SwapTransactionRepository,
    private val vaultRepository: VaultRepository,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,
    private val mapDepositTransactionUiModel: DepositTransactionToUiModelMapper,
    private val mapSwapTransactionToUiModel: SwapTransactionToUiModelMapper,
    generateServiceName: GenerateServiceName,
    private val routerApi: RouterApi,
    private val pullTssMessages: PullTssMessagesUseCase,
    private val broadcastTx: BroadcastTxUseCase,
    private val solanaApi: SolanaApi,
    private val payloadToProtoMapper: PayloadToProtoMapper,
    private val discoverParticipantsUseCase: DiscoverParticipantsUseCase,
    private val addressBookRepository: AddressBookRepository,
    private val transactionStatusServiceManager: TransactionStatusServiceManager,
    private val txStatusConfigurationProvider: TxStatusConfigurationProvider,
    private val pushNotificationManager: PushNotificationManager,
    private val snackbarFlow: SnackbarFlow,
    private val transactionHistoryDataMapper: SendTransactionHistoryDataMapper,
    private val depositTransactionHistoryDataMapper: DepositTransactionHistoryDataMapper,
    private val swapTransactionToHistoryDataMapper: SwapTransactionToHistoryDataMapper,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val keysignViewModelFactory: KeysignViewModel.Factory,
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

    val currentState: MutableStateFlow<KeysignFlowState> =
        MutableStateFlow(KeysignFlowState.PeerDiscovery)
    val selection = MutableStateFlow<List<String>>(emptyList())
    val keysignMessage: MutableStateFlow<String>
        get() = _keysignMessage

    val participants = MutableStateFlow<List<String>>(emptyList())
    val networkOption: MutableStateFlow<NetworkOption> = MutableStateFlow(NetworkOption.Internet)

    private val args = savedStateHandle.toRoute<Route.Keysign.Keysign>()
    private val password = args.password
    private val transactionId = args.transactionId

    val isFastSign: Boolean
        get() = !password.isNullOrBlank()

    private val isRelayEnabled: Boolean
        get() = networkOption.value == NetworkOption.Internet || isFastSign

    val isLoading = MutableStateFlow(false)
    private val _isDataLoaded = MutableStateFlow(false)
    val isDataLoaded: StateFlow<Boolean> = _isDataLoaded

    private var transactionTypeUiModel: TransactionTypeUiModel? = null
    private var transactionHistoryData = MutableStateFlow<TransactionHistoryData?>(null)

    private val tssKeysignType: TssKeyType
        get() = _keysignPayload?.coin?.chain?.TssKeysignType ?: TssKeyType.ECDSA

    private var discoverParticipantsJob: Job? = null
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

    val uiState = MutableStateFlow(KeysignFlowUiState())

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
            val modifiedKeysignPayload = updateSolanaKeysignPayload(keysignPayload)
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
                            uiState.update { it.copy(amount = amount) }
                        }
                    }
                    launch {
                        shareViewModel.toAmount.collect { toAmount ->
                            uiState.update { it.copy(toAmount = toAmount) }
                        }
                    }
                    launch {
                        shareViewModel.qrBitmapPainter.collect { painter ->
                            uiState.update { it.copy(qrBitmapPainter = painter) }
                        }
                    }
                }

            val srcLogoModel = keysignPayload?.coin?.tokenLogoRes()
            val dstLogoModel = keysignPayload?.swapPayload?.dstToken?.tokenLogoRes()
            uiState.update {
                it.copy(
                    vault = vault,
                    isSwap = shareViewModel.keysignPayload?.swapPayload != null,
                    toAddress = keysignPayload?.toAddress ?: "",
                    enableNotification = vault.isSecureVault(),
                    srcTokenLogoRes = srcLogoModel,
                    dstTokenLogoRes = dstLogoModel,
                )
            }

            this.selection.value = listOf(vault.localPartyID)
            _serverAddress = Endpoints.VULTISIG_RELAY_URL
            updateKeysignPayload(context)
            updateTransactionUiModel(keysignPayload, customMessagePayload, txType)
        } catch (e: Exception) {
            Timber.e(e)
            moveToState(
                Error(e.message?.asUiText() ?: UiText.StringResource(R.string.unknown_error))
            )
        }
    }

    private suspend fun updateSolanaKeysignPayload(keysignPayload: KeysignPayload?) =
        keysignPayload
            ?.takeIf { it.blockChainSpecific is BlockChainSpecific.Solana }
            ?.let { payload ->
                payload.copy(
                    blockChainSpecific =
                        (payload.blockChainSpecific as BlockChainSpecific.Solana).copy(
                            recentBlockHash = solanaApi.getRecentBlockHash()
                        )
                )
            } ?: keysignPayload

    private suspend fun updateKeysignPayload(context: Context) {
        stopParticipantDiscovery()
        val vault =
            _currentVault
                ?: run {
                    moveToState(KeysignFlowState.Error("Vault is not set".asUiText()))
                    return
                }

        if (!isRelayEnabled) {
            startMediatorService(context)
        } else {
            _serverAddress = Endpoints.VULTISIG_RELAY_URL
            withContext(Dispatchers.IO) {
                startSession(_serverAddress, _sessionID, vault.localPartyID)
            }

            startParticipantDiscovery(vault)
        }

        val keysignPayload = _keysignPayload
        val keysignPayloadProto = payloadToProtoMapper(keysignPayload)

        val keysignProto =
            protoBuf.encodeToByteArray(
                KeysignMessageProto(
                    sessionId = _sessionID,
                    serviceName = _serviceName,
                    keysignPayload = keysignPayloadProto,
                    encryptionKeyHex = _encryptionKeyHex,
                    useVultisigRelay = isRelayEnabled,
                    customMessagePayload = customMessagePayload,
                )
            )

        Timber.d("keysignProto: $keysignProto")

        var data = compressQr(keysignProto).encodeBase64()
        if (keysignPayloadProto != null && routerApi.shouldUploadPayload(data)) {
            protoBuf.encodeToByteArray(keysignPayloadProto).let {
                compressQr(it).encodeBase64().let { compressedData ->
                    val hash = routerApi.uploadPayload(_serverAddress, compressedData)
                    protoBuf
                        .encodeToByteArray(
                            KeysignMessageProto(
                                sessionId = _sessionID,
                                serviceName = _serviceName,
                                encryptionKeyHex = _encryptionKeyHex,
                                useVultisigRelay = isRelayEnabled,
                                payloadId = hash,
                            )
                        )
                        .let { compressedData -> data = compressQr(compressedData).encodeBase64() }
                }
            }
        }
        _keysignMessage.value =
            "https://vultisig.com?type=SignTransaction&resharePrefix=${vault.resharePrefix}&vault=${vault.pubKeyECDSA}&jsonData=" +
                data

        addressProvider.update(_keysignMessage.value)
        if (vault.isSecureVault()) sendNotification()
    }

    fun sendNotification() {
        if (uiState.value.resendCooldownSeconds > 0) return
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
                var seconds = 60
                while (seconds > 0) {
                    uiState.update { it.copy(resendCooldownSeconds = seconds) }
                    delay(1.seconds)
                    seconds--
                }
                uiState.update { it.copy(resendCooldownSeconds = 0) }
                lastNotifiedQrData = ""
            }
    }

    private fun startParticipantDiscovery(vault: Vault) {
        discoverParticipantsJob?.cancel()
        discoverParticipantsJob =
            viewModelScope.launch {
                discoverParticipantsUseCase(_serverAddress, _sessionID, vault.localPartyID)
                    .collect { participants ->
                        val existingParticipants =
                            this@KeysignFlowViewModel.participants.value.toSet()
                        val newParticipants = participants - existingParticipants
                        this@KeysignFlowViewModel.participants.update { participants }
                        newParticipants.forEach(::addParticipant)
                    }
            }
    }

    private fun updateTransactionUiModel(
        keysignPayload: KeysignPayload?,
        customMessagePayload: CustomMessagePayload?,
        txType: Route.Keysign.Keysign.TxType,
    ) {
        if (keysignPayload != null) {
            transactionId.let {
                val isSwap =
                    keysignPayload.swapPayload != null ||
                        txType == Route.Keysign.Keysign.TxType.Swap

                viewModelScope.launch {
                    val isDeposit =
                        when (val specific = keysignPayload.blockChainSpecific) {
                            is BlockChainSpecific.MayaChain -> specific.isDeposit
                            is BlockChainSpecific.THORChain -> specific.isDeposit
                            is BlockChainSpecific.Ton -> specific.isDeposit
                            is BlockChainSpecific.Cosmos ->
                                specific.transactionType ==
                                    TransactionType.TRANSACTION_TYPE_IBC_TRANSFER ||
                                    try {
                                        depositTransactionRepository.getTransaction(transactionId)
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }

                            else -> txType == Route.Keysign.Keysign.TxType.Deposit
                        }

                    when {
                        isSwap -> {
                            val swapTransactionUiModel =
                                mapSwapTransactionToUiModel(
                                    swapTransactionRepository.getTransaction(transactionId)
                                )
                            transactionTypeUiModel =
                                TransactionTypeUiModel.Swap(swapTransactionUiModel)
                            transactionHistoryData.update {
                                swapTransactionToHistoryDataMapper(swapTransactionUiModel)
                            }
                        }

                        isDeposit -> {
                            val depositTransactionUiModel =
                                mapDepositTransactionUiModel(
                                    depositTransactionRepository.getTransaction(transactionId)
                                )
                            transactionTypeUiModel =
                                TransactionTypeUiModel.Deposit(depositTransactionUiModel)
                            transactionHistoryData.update {
                                depositTransactionHistoryDataMapper(depositTransactionUiModel)
                            }
                        }

                        else -> {
                            val tx =
                                transactionRepository.getTransaction(transactionId)
                                    ?: error("Transaction not found: $transactionId")
                            val transactionDetailsUiModel = mapTransactionToUiModel(tx)
                            transactionHistoryData.update {
                                transactionHistoryDataMapper(transactionDetailsUiModel)
                            }
                            transactionTypeUiModel =
                                TransactionTypeUiModel.Send(transactionDetailsUiModel)
                        }
                    }
                    _isDataLoaded.value = true
                }
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

    private val serviceStartedReceiver: BroadcastReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == MediatorService.SERVICE_ACTION) {
                    Timber.tag("KeysignFlowViewModel").d("onReceive: Mediator service started")
                    val vault =
                        _currentVault
                            ?: run {
                                moveToState(KeysignFlowState.Error("Vault is not set".asUiText()))
                                return
                            }
                    // send a request to local mediator server to start the session
                    viewModelScope.launch(Dispatchers.IO) {
                        startSessionWithRetry(_serverAddress, _sessionID, vault.localPartyID)
                    }
                    // kick off discovery
                    startParticipantDiscovery(vault)
                }
            }
        }

    private fun stopService(context: Context) {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stopService: Mediator service stopped")
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun startMediatorService(context: Context) {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(serviceStartedReceiver, filter)
        }

        MediatorService.start(context, _serviceName)
    }

    private suspend fun startSession(serverAddr: String, sessionID: String, localPartyID: String) {
        // start the session
        try {
            sessionApi.startSession(serverAddr, sessionID, listOf(localPartyID))

            Timber.tag("KeysignFlowViewModel").d("startSession: Session started")

            if (!password.isNullOrBlank()) {
                val vault =
                    _currentVault
                        ?: run {
                            Timber.e("Vault is not set when joining keysign in startSession")
                            moveToState(KeysignFlowState.Error("Vault is not set".asUiText()))
                            return
                        }
                val chain = _keysignPayload?.coin?.chain?.raw ?: ""
                val isEcdsa = tssKeysignType == TssKeyType.ECDSA
                Timber.tag("KeysignFlowViewModel")
                    .d(
                        "joinKeysign: chain=$chain, isEcdsa=$isEcdsa, tssKeysignType=$tssKeysignType, messages=${messagesToSign.map { it.take(16) }}"
                    )
                vultiSignerRepository.joinKeysign(
                    JoinKeysignRequestJson(
                        publicKeyEcdsa = vault.pubKeyECDSA,
                        messages = messagesToSign,
                        sessionId = sessionID,
                        hexEncryptionKey = _encryptionKeyHex,
                        derivePath =
                            (_keysignPayload?.coin?.coinType ?: CoinType.ETHEREUM).derivationPath(),
                        isEcdsa = isEcdsa,
                        password = password,
                        chain = chain,
                        mldsa = tssKeysignType == TssKeyType.MLDSA,
                    )
                )
                Timber.tag("KeysignFlowViewModel").d("joinKeysign: server notified successfully")
            }
        } catch (e: Exception) {
            Timber.tag("KeysignFlowViewModel").e("startSession: ${e.stackTraceToString()}")
        }
    }

    private suspend fun startSessionWithRetry(
        serverAddr: String,
        sessionID: String,
        localPartyID: String,
    ) {
        var delayMs = 200L
        repeat(4) { attempt ->
            try {
                Timber.tag("KeysignFlowViewModel")
                    .d("startSessionWithRetry: Attempt ${attempt + 1}")
                sessionApi.startSession(serverAddr, sessionID, listOf(localPartyID))
                Timber.tag("KeysignFlowViewModel").d("startSession: Session started")
                if (!password.isNullOrBlank()) {
                    val vault =
                        _currentVault
                            ?: run {
                                Timber.e("Vault is not set when joining keysign in startSession")
                                moveToState(KeysignFlowState.Error("Vault is not set".asUiText()))
                                return
                            }
                    vultiSignerRepository.joinKeysign(
                        JoinKeysignRequestJson(
                            publicKeyEcdsa = vault.pubKeyECDSA,
                            messages = messagesToSign,
                            sessionId = sessionID,
                            hexEncryptionKey = _encryptionKeyHex,
                            derivePath =
                                (_keysignPayload?.coin?.coinType ?: CoinType.ETHEREUM)
                                    .derivationPath(),
                            isEcdsa = tssKeysignType == TssKeyType.ECDSA,
                            password = password,
                            chain = _keysignPayload?.coin?.chain?.name ?: "",
                        )
                    )
                }
                return
            } catch (e: Exception) {
                Timber.tag("KeysignFlowViewModel")
                    .e(e, "startSessionWithRetry: Attempt ${attempt + 1} failed")
                if (attempt < 3) {
                    delay(delayMs)
                    delayMs *= 2
                } else {
                    Timber.tag("KeysignFlowViewModel").e("All attempts to start session failed")
                    moveToState(KeysignFlowState.Error("Failed to start session".asUiText()))
                }
            }
        }
    }

    fun addParticipant(participant: String) {
        val currentList = selection.value
        if (currentList.contains(participant)) return
        selection.value = currentList + participant
    }

    private fun removeParticipant(participant: String) {
        selection.value -= participant
    }

    fun handleParticipant(participant: String) {
        if (participant in selection.value) {
            removeParticipant(participant)
        } else {
            addParticipant(participant)
        }
    }

    fun moveToState(nextState: KeysignFlowState) {
        try {
            if (nextState == KeysignFlowState.Keysign) {
                cleanQrAddress()
            }
            currentState.update { nextState }
        } catch (e: Exception) {
            isLoading.value = false
            moveToState(
                Error(e.message?.asUiText() ?: UiText.StringResource(R.string.unknown_error))
            )
        }
    }

    fun moveToKeysignState() {
        viewModelScope.launch {
            isLoading.value = true
            stopParticipantDiscovery()
            moveToState(KeysignFlowState.Keysign)
            isLoading.value = false
        }
    }

    fun stopParticipantDiscovery() {
        discoverParticipantsJob?.cancel()
    }

    private fun cleanQrAddress() {
        addressProvider.clean()
    }

    fun changeNetworkPromptOption(option: NetworkOption, context: Context) {
        if (networkOption.value == option) return
        networkOption.value = option
        _serverAddress =
            when (option) {
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
                val keygenCommittee = selection.value
                sessionApi.startWithCommittee(_serverAddress, _sessionID, keygenCommittee)
                Timber.d("Keysign started")
            } catch (e: Exception) {
                Timber.e("Failed to start keysign: ${e.stackTraceToString()}")
            }
        }
    }

    override fun onCleared() {
        cleanQrAddress()
        try {
            context.unregisterReceiver(serviceStartedReceiver)
        } catch (_: IllegalArgumentException) {
            // receiver was already unregistered or never registered
        }
        stopService(context)
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
