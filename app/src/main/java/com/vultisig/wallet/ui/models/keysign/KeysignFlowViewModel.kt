@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
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
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.GenerateServiceName
import com.vultisig.wallet.data.usecases.tss.DiscoverParticipantsUseCase
import com.vultisig.wallet.data.usecases.tss.PullTssMessagesUseCase
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.peer.NetworkOption
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.encodeBase64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
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
import java.util.UUID
import javax.inject.Inject

internal sealed class KeysignFlowState {
    data object PeerDiscovery : KeysignFlowState()
    data object Keysign : KeysignFlowState()
    data class Error (val errorMessage: String) : KeysignFlowState()
}

@HiltViewModel
internal class KeysignFlowViewModel @Inject constructor(
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
    private val mapTransactionToUiModel: TransactionToUiModelMapper,
    private val mapDepositTransactionUiModel: DepositTransactionToUiModelMapper,
    private val mapSwapTransactionToUiModel: SwapTransactionToUiModelMapper,
    private val generateServiceName: GenerateServiceName,
    private val routerApi: RouterApi,
    private val pullTssMessages: PullTssMessagesUseCase,
    private val broadcastTx: BroadcastTxUseCase,
    private val solanaApi: SolanaApi,
    private val payloadToProtoMapper: PayloadToProtoMapper,
    private val discoverParticipantsUseCase: DiscoverParticipantsUseCase,
    private val addressBookRepository: AddressBookRepository,
) : ViewModel() {
    private val _sessionID: String = UUID.randomUUID().toString()
    private val _serviceName: String = generateServiceName()
    private var _serverAddress: String = LOCAL_MEDIATOR_SERVER_URL
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex
    private var _currentVault: Vault? = null
    private var _keysignPayload: KeysignPayload? = null
    private var customMessagePayload: CustomMessagePayload? = null
    private val _keysignMessage: MutableState<String> = mutableStateOf("")
    private var messagesToSign = emptyList<String>()

    val currentState: MutableStateFlow<KeysignFlowState> =
        MutableStateFlow(KeysignFlowState.PeerDiscovery)
    val selection = MutableLiveData<List<String>>()
    val keysignMessage: MutableState<String>
        get() = _keysignMessage

    val participants = MutableStateFlow<List<String>>(emptyList())
    val networkOption: MutableState<NetworkOption> =
        mutableStateOf(NetworkOption.Internet)

    private val args = savedStateHandle.toRoute<Route.Keysign.Keysign>()
    private val password = args.password
    private val transactionId = args.transactionId

    val isFastSign: Boolean
        get() = !password.isNullOrBlank()

    private val isRelayEnabled by derivedStateOf {
        networkOption.value == NetworkOption.Internet || isFastSign
    }

    val isLoading = MutableStateFlow(false)

    private var transactionTypeUiModel: TransactionTypeUiModel? = null

    private val tssKeysignType: TssKeyType
        get() = _keysignPayload?.coin?.chain?.TssKeysignType ?: TssKeyType.ECDSA


    private var discoverParticipantsJob: Job? = null

    val keysignViewModel: KeysignViewModel
        get() = KeysignViewModel(
            vault = _currentVault!!,
            keysignCommittee = selection.value!!,
            serverUrl = _serverAddress,
            sessionId = _sessionID,
            encryptionKeyHex = _encryptionKeyHex,
            messagesToSign = messagesToSign,
            keyType = tssKeysignType,
            keysignPayload = _keysignPayload,
            customMessagePayload = customMessagePayload,
            thorChainApi = thorChainApi,
            broadcastTx = broadcastTx,
            evmApiFactory = evmApiFactory,
            explorerLinkRepository = explorerLinkRepository,
            sessionApi = sessionApi,
            navigator = navigator,
            encryption = encryption,
            featureFlagApi = featureFlagApi,
            transactionTypeUiModel = transactionTypeUiModel,
            pullTssMessages = pullTssMessages,
            isInitiatingDevice = true,
            addressBookRepository = addressBookRepository,
        )

    init {
        viewModelScope.launch {
            currentState.collect { state ->
                if (state == KeysignFlowState.Keysign) {
                    startKeysign()
                }
            }
        }
    }

    suspend fun setData(
        vault: Vault,
        context: Context,
        keysignPayload: KeysignPayload?,
        customMessagePayload: CustomMessagePayload?
    ) {
        try {
            _currentVault = vault
            val modifiedKeysignPayload = updateSolanaKeysignPayload(keysignPayload)
            _keysignPayload = modifiedKeysignPayload
            this.customMessagePayload = customMessagePayload
            messagesToSign = when {
                modifiedKeysignPayload != null ->
                    SigningHelper.getKeysignMessages(
                        payload = modifiedKeysignPayload,
                        vault = vault,
                    )

                customMessagePayload != null ->
                    SigningHelper.getKeysignMessages(
                        messagePayload = customMessagePayload
                    )

                else -> error("Payload is null")
            }

            this.selection.value = listOf(vault.localPartyID)
            _serverAddress = Endpoints.VULTISIG_RELAY_URL
            updateKeysignPayload(context)
            updateTransactionUiModel(keysignPayload, customMessagePayload)
        } catch (e: Exception) {
            Timber.e(e)
            moveToState(KeysignFlowState.Error(e.message.toString()))
        }
    }

    private suspend fun updateSolanaKeysignPayload(keysignPayload: KeysignPayload?) =
        keysignPayload?.takeIf { it.blockChainSpecific is BlockChainSpecific.Solana }
            ?.let { payload ->
                payload.copy(
                    blockChainSpecific = (payload.blockChainSpecific as BlockChainSpecific.Solana)
                        .copy(recentBlockHash = solanaApi.getRecentBlockHash())
                )
            } ?: keysignPayload

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    private suspend fun updateKeysignPayload(context: Context) {
        stopParticipantDiscovery()
        _currentVault ?: run {
            moveToState(KeysignFlowState.Error("Vault is not set"))
            return
        }
        val vault = _currentVault!!

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

        val keysignProto = protoBuf.encodeToByteArray(
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
                    protoBuf.encodeToByteArray(
                        KeysignMessageProto(
                            sessionId = _sessionID,
                            serviceName = _serviceName,
                            encryptionKeyHex = _encryptionKeyHex,
                            useVultisigRelay = isRelayEnabled,
                            payloadId = hash,
                        )
                    ).let { compressedData ->
                        data = compressQr(compressedData).encodeBase64()
                    }
                }
            }
        }
        _keysignMessage.value =
            "https://vultisig.com?type=SignTransaction&resharePrefix=${vault.resharePrefix}&vault=${vault.pubKeyECDSA}&jsonData=" + data

        addressProvider.update(_keysignMessage.value)
    }

    private fun startParticipantDiscovery(vault: Vault) {
        discoverParticipantsJob?.cancel()
        discoverParticipantsJob = viewModelScope.launch {
            discoverParticipantsUseCase(
                _serverAddress,
                _sessionID,
                vault.localPartyID
            ).collect { participants ->
                val existingParticipants = this@KeysignFlowViewModel.participants.value.toSet()
                val newParticipants = participants - existingParticipants
                this@KeysignFlowViewModel.participants.update { participants }
                newParticipants.forEach(::addParticipant)
            }
        }
    }

    private fun updateTransactionUiModel(
        keysignPayload: KeysignPayload?,
        customMessagePayload: CustomMessagePayload?,
    ) {
        if (keysignPayload != null) {
            transactionId?.let {
                val isSwap = keysignPayload.swapPayload != null
                viewModelScope.launch {
                    val isDeposit = when (val specific = keysignPayload.blockChainSpecific) {
                        is BlockChainSpecific.MayaChain -> specific.isDeposit
                        is BlockChainSpecific.THORChain -> specific.isDeposit
                        is BlockChainSpecific.Ton -> specific.isDeposit
                        is BlockChainSpecific.Ethereum -> specific.isDeposit
                        is BlockChainSpecific.UTXO -> specific.isDeposit
                        is BlockChainSpecific.Cosmos ->
                            specific.transactionType ==
                                    TransactionType.TRANSACTION_TYPE_IBC_TRANSFER ||
                                    try {
                                        depositTransactionRepository.getTransaction(transactionId)
                                        true
                                    } catch (e: Exception) {
                                        false
                                    }

                        else -> false
                    }

                    transactionTypeUiModel = when {
                        isSwap -> TransactionTypeUiModel.Swap(
                            mapSwapTransactionToUiModel(
                                swapTransactionRepository.getTransaction(transactionId)
                            )
                        )

                        isDeposit -> TransactionTypeUiModel.Deposit(
                            mapDepositTransactionUiModel(
                                depositTransactionRepository.getTransaction(transactionId)
                            )
                        )

                        else -> TransactionTypeUiModel.Send(
                            mapTransactionToUiModel(
                                transactionRepository.getTransaction(
                                    transactionId
                                )
                            )
                        )
                    }
                }
            }
        } else {
            transactionTypeUiModel = TransactionTypeUiModel.SignMessage(
                model = SignMessageTransactionUiModel(
                    method = customMessagePayload?.method ?: "",
                    message = customMessagePayload?.message ?: "",
                )
            )
        }
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Timber.tag("KeysignFlowViewModel").d("onReceive: Mediator service started")
                if (_currentVault == null) {
                    moveToState(KeysignFlowState.Error("Vault is not set"))
                    return
                }
                // send a request to local mediator server to start the session
                viewModelScope.launch(Dispatchers.IO) {
                    delay(1000) // back off a second
                    startSession(_serverAddress, _sessionID, _currentVault!!.localPartyID)
                }
                // kick off discovery
                startParticipantDiscovery(_currentVault!!)
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

    private suspend fun startSession(
        serverAddr: String,
        sessionID: String,
        localPartyID: String,
    ) {
        // start the session
        try {
            sessionApi.startSession(serverAddr, sessionID, listOf(localPartyID))

            Timber.tag("KeysignFlowViewModel").d("startSession: Session started")

            if (!password.isNullOrBlank()) {
                val vault = _currentVault!!
                vultiSignerRepository.joinKeysign(
                    JoinKeysignRequestJson(
                        publicKeyEcdsa = vault.pubKeyECDSA,
                        messages = messagesToSign,
                        sessionId = sessionID,
                        hexEncryptionKey = _encryptionKeyHex,
                        derivePath = (_keysignPayload?.coin?.coinType
                            ?: CoinType.ETHEREUM).derivationPath(),
                        isEcdsa = tssKeysignType == TssKeyType.ECDSA,
                        password = password,
                        chain = _keysignPayload?.coin?.chain?.name ?: "",
                    )
                )
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

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    fun moveToState(nextState: KeysignFlowState) {
        try {
            if (nextState == KeysignFlowState.Keysign) {
                cleanQrAddress()
            }
            currentState.update { nextState }
        } catch (e: Exception) {
            isLoading.value = false
            moveToState(KeysignFlowState.Error(e.message.toString()))
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
        _serverAddress = when (option) {
            NetworkOption.Local -> {
                LOCAL_MEDIATOR_SERVER_URL
            }

            NetworkOption.Internet -> {
                Endpoints.VULTISIG_RELAY_URL
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            updateKeysignPayload(context)
        }
    }

    private suspend fun startKeysign() {
        withContext(Dispatchers.IO) {
            try {
                val keygenCommittee = selection.value ?: emptyList()
                sessionApi.startWithCommittee(_serverAddress, _sessionID, keygenCommittee)
                Timber.d("Keysign started")
            } catch (e: Exception) {
                Timber.e("Failed to start keysign: ${e.stackTraceToString()}")
            }
        }
    }

    override fun onCleared() {
        cleanQrAddress()
        stopService(context)
        super.onCleared()
    }

    fun tryAgain() {
        back()
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun complete() {
        viewModelScope.launch {
            navigator.route(
                Route.Home(),
                NavigationOptions(
                    clearBackStack = true
                )
            )
        }
    }
}