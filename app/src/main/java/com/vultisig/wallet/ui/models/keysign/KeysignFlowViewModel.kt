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
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ParticipantDiscovery
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.api.models.signer.JoinKeysignRequestJson
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.Utils
import com.vultisig.wallet.data.mediator.MediatorService
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.ERC20ApprovePayload
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.SwapTransactionRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VultiSignerRepository
import com.vultisig.wallet.data.usecases.CompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.ui.models.AddressProvider
import com.vultisig.wallet.ui.models.mappers.DepositTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToUiModelMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.utils.NetworkPromptOption
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.ktor.util.encodeBase64
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import vultisig.keysign.v1.CosmosSpecific
import vultisig.keysign.v1.Erc20ApprovePayload
import vultisig.keysign.v1.EthereumSpecific
import vultisig.keysign.v1.MAYAChainSpecific
import vultisig.keysign.v1.OneInchQuote
import vultisig.keysign.v1.OneInchSwapPayload
import vultisig.keysign.v1.OneInchTransaction
import vultisig.keysign.v1.PolkadotSpecific
import vultisig.keysign.v1.SolanaSpecific
import vultisig.keysign.v1.SuiSpecific
import vultisig.keysign.v1.THORChainSpecific
import vultisig.keysign.v1.THORChainSwapPayload
import vultisig.keysign.v1.UTXOSpecific
import vultisig.keysign.v1.UtxoInfo
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

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
    private val blockChairApi: BlockChairApi,
    private val evmApiFactory: EvmApiFactory,
    private val mayaChainApi: MayaChainApi,
    private val cosmosApiFactory: CosmosApiFactory,
    private val solanaApi: SolanaApi,
    private val polkadotApi: PolkadotApi,
    private val suiApi: SuiApi,
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
) : ViewModel() {
    private val _sessionID: String = UUID.randomUUID().toString()
    private val _serviceName: String = "vultisigApp-${Random.nextInt(1, 1000)}"
    private var _serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var _participantDiscovery: ParticipantDiscovery? = null
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex
    private var _currentVault: Vault? = null
    private var _keysignPayload: KeysignPayload? = null
    private val _keysignMessage: MutableState<String> = mutableStateOf("")
    private var messagesToSign = emptyList<String>()

    var currentState: MutableStateFlow<KeysignFlowState> =
        MutableStateFlow(KeysignFlowState.PeerDiscovery)
    val selection = MutableLiveData<List<String>>()
    val localPartyID: String?
        get() = _currentVault?.localPartyID
    val keysignMessage: MutableState<String>
        get() = _keysignMessage

    val participants: MutableLiveData<List<String>>
        get() = _participantDiscovery?.participants ?: MutableLiveData(listOf())

    val networkOption: MutableState<NetworkPromptOption> =
        mutableStateOf(NetworkPromptOption.INTERNET)

    val password = savedStateHandle.get<String?>(SendDst.ARG_PASSWORD)
    val transactionId = savedStateHandle.get<String>(SendDst.ARG_TRANSACTION_ID)

    val isFastSign: Boolean
        get() = password != null

    private val isRelayEnabled by derivedStateOf {
        networkOption.value == NetworkPromptOption.INTERNET || isFastSign
    }

    private var transitionTypeUiModel: TransitionTypeUiModel? = null


    val keysignViewModel: KeysignViewModel
        get() = KeysignViewModel(
            vault = _currentVault!!,
            keysignCommittee = selection.value!!,
            serverAddress = _serverAddress,
            sessionId = _sessionID,
            encryptionKeyHex = _encryptionKeyHex,
            messagesToSign = messagesToSign,
            keyType = _keysignPayload?.coin?.chain?.TssKeysignType ?: TssKeyType.ECDSA,
            keysignPayload = _keysignPayload!!,
            thorChainApi = thorChainApi,
            blockChairApi = blockChairApi,
            evmApiFactory = evmApiFactory,
            mayaChainApi = mayaChainApi,
            cosmosApiFactory = cosmosApiFactory,
            solanaApi = solanaApi,
            polkadotApi = polkadotApi,
            explorerLinkRepository = explorerLinkRepository,
            sessionApi = sessionApi,
            suiApi = suiApi,
            navigator = navigator,
            encryption = encryption,
            featureFlagApi = featureFlagApi,
            transitionTypeUiModel = transitionTypeUiModel
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

    suspend fun setData(vault: Vault, context: Context, keysignPayload: KeysignPayload) {
        try {
            _currentVault = vault
            _keysignPayload = keysignPayload
            messagesToSign = SigningHelper.getKeysignMessages(
                payload = _keysignPayload!!,
                vault = _currentVault!!,
            )
            this.selection.value = listOf(vault.localPartyID)
            _serverAddress = Endpoints.VULTISIG_RELAY
            updateKeysignPayload(context)
            updateTransactionUiModel(keysignPayload)
        } catch (e: Exception) {
            Timber.e(e)
            moveToState(KeysignFlowState.Error(e.message.toString()))
        }
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    private suspend fun updateKeysignPayload(context: Context) {
        stopParticipantDiscovery()
        _currentVault ?: run {
            moveToState(KeysignFlowState.Error("Vault is not set"))
            return
        }
        val vault = _currentVault!!
        _participantDiscovery = ParticipantDiscovery(
            _serverAddress,
            _sessionID,
            vault.localPartyID,
            sessionApi
        )

        val keysignPayload = _keysignPayload!!
        val swapPayload = keysignPayload.swapPayload
        val approvePayload = keysignPayload.approvePayload

        val specific = keysignPayload.blockChainSpecific

        val keysignProto = protoBuf.encodeToByteArray(
            KeysignMessageProto(
                sessionId = _sessionID,
                serviceName = _serviceName,
                keysignPayload = KeysignPayloadProto(
                    coin = keysignPayload.coin.toCoinProto(),
                    toAddress = keysignPayload.toAddress,
                    toAmount = keysignPayload.toAmount.toString(),
                    memo = keysignPayload.memo,
                    vaultLocalPartyId = keysignPayload.vaultLocalPartyID,
                    vaultPublicKeyEcdsa = keysignPayload.vaultPublicKeyECDSA,
                    utxoSpecific = if (specific is BlockChainSpecific.UTXO) {
                        UTXOSpecific(
                            byteFee = specific.byteFee.toString(),
                            sendMaxAmount = specific.sendMaxAmount,
                        )
                    } else null,
                    utxoInfo = keysignPayload.utxos.map {
                        UtxoInfo(
                            hash = it.hash,
                            amount = it.amount,
                            index = it.index,
                        )
                    },
                    ethereumSpecific = if (specific is BlockChainSpecific.Ethereum) {
                        EthereumSpecific(
                            maxFeePerGasWei = specific.maxFeePerGasWei.toString(),
                            priorityFee = specific.priorityFeeWei.toString(),
                            nonce = specific.nonce.toLong(),
                            gasLimit = specific.gasLimit.toString(),
                        )
                    } else null,
                    thorchainSpecific = if (specific is BlockChainSpecific.THORChain) {
                        THORChainSpecific(
                            accountNumber = specific.accountNumber.toString().toULong(),
                            sequence = specific.sequence.toString().toULong(),
                            fee = specific.fee.toString().toULong(),
                            isDeposit = specific.isDeposit,
                        )
                    } else null,
                    mayaSpecific = if (specific is BlockChainSpecific.MayaChain) {
                        MAYAChainSpecific(
                            accountNumber = specific.accountNumber.toString().toULong(),
                            sequence = specific.sequence.toString().toULong(),
                            isDeposit = specific.isDeposit,
                        )
                    } else null,
                    cosmosSpecific = if (specific is BlockChainSpecific.Cosmos) {
                        CosmosSpecific(
                            accountNumber = specific.accountNumber.toString().toULong(),
                            sequence = specific.sequence.toString().toULong(),
                            gas = specific.gas.toString().toULong(),
                        )
                    } else null,
                    solanaSpecific = if (specific is BlockChainSpecific.Solana) {
                        SolanaSpecific(
                            recentBlockHash = specific.recentBlockHash,
                            priorityFee = specific.priorityFee.toString(),
                            toTokenAssociatedAddress = specific.toAddressPubKey,
                            fromTokenAssociatedAddress = specific.fromAddressPubKey,
                        )
                    } else null,
                    polkadotSpecific = if (specific is BlockChainSpecific.Polkadot) {
                        PolkadotSpecific(
                            recentBlockHash = specific.recentBlockHash,
                            nonce = specific.nonce.toString().toULong(),
                            currentBlockNumber = specific.currentBlockNumber.toString(),
                            specVersion = specific.specVersion,
                            transactionVersion = specific.transactionVersion,
                            genesisHash = specific.genesisHash,
                        )
                    } else null,
                    suicheSpecific = if (specific is BlockChainSpecific.Sui) {
                        SuiSpecific(
                            referenceGasPrice = specific.referenceGasPrice.toString(),
                            coins = specific.coins,
                        )
                    } else null,
                    thorchainSwapPayload = if (swapPayload is SwapPayload.ThorChain) {
                        val from = swapPayload.data
                        THORChainSwapPayload(
                            fromAddress = from.fromAddress,
                            fromCoin = from.fromCoin.toCoinProto(),
                            toCoin = from.toCoin.toCoinProto(),
                            vaultAddress = from.vaultAddress,
                            routerAddress = from.routerAddress,
                            fromAmount = from.fromAmount.toString(),
                            toAmountDecimal = from.toAmountDecimal.toPlainString(),
                            toAmountLimit = from.toAmountLimit,
                            streamingInterval = from.streamingInterval,
                            streamingQuantity = from.streamingQuantity,
                            expirationTime = from.expirationTime,
                            isAffiliate = from.isAffiliate,
                        )
                    } else null,
                    mayachainSwapPayload = if (swapPayload is SwapPayload.MayaChain) {
                        val from = swapPayload.data
                        THORChainSwapPayload(
                            fromAddress = from.fromAddress,
                            fromCoin = from.fromCoin.toCoinProto(),
                            toCoin = from.toCoin.toCoinProto(),
                            vaultAddress = from.vaultAddress,
                            routerAddress = from.routerAddress,
                            fromAmount = from.fromAmount.toString(),
                            toAmountDecimal = from.toAmountDecimal.toPlainString(),
                            toAmountLimit = from.toAmountLimit,
                            streamingInterval = from.streamingInterval,
                            streamingQuantity = from.streamingQuantity,
                            expirationTime = from.expirationTime,
                            isAffiliate = from.isAffiliate,
                        )
                    } else null,
                    oneinchSwapPayload = if (swapPayload is SwapPayload.OneInch) {
                        val from = swapPayload.data
                        OneInchSwapPayload(
                            fromCoin = from.fromCoin.toCoinProto(),
                            toCoin = from.toCoin.toCoinProto(),
                            fromAmount = from.fromAmount.toString(),
                            toAmountDecimal = from.toAmountDecimal.toPlainString(),
                            quote = from.quote.let { it ->
                                OneInchQuote(
                                    dstAmount = it.dstAmount,
                                    tx = it.tx.let {
                                        OneInchTransaction(
                                            from = it.from,
                                            to = it.to,
                                            `data` = it.data,
                                            `value` = it.value,
                                            gasPrice = it.gasPrice,
                                            gas = it.gas,
                                        )
                                    }
                                )
                            }
                        )
                    } else null,
                    erc20ApprovePayload = if (approvePayload is ERC20ApprovePayload) {
                        Erc20ApprovePayload(
                            spender = approvePayload.spender,
                            amount = approvePayload.amount.toString(),
                        )
                    } else null,
                ),
                encryptionKeyHex = _encryptionKeyHex,
                useVultisigRelay = isRelayEnabled
            )
        )

        Timber.d("keysignProto: $keysignProto")

        val data = compressQr(keysignProto).encodeBase64()

        _keysignMessage.value =
            "vultisig://vultisig.com?type=SignTransaction&resharePrefix=${vault.resharePrefix}&vault=${vault.pubKeyECDSA}&jsonData=" + data


        addressProvider.update(_keysignMessage.value)
        if (!isRelayEnabled) {
            startMediatorService(context)
        } else {
            _serverAddress = Endpoints.VULTISIG_RELAY
            withContext(Dispatchers.IO) {
                startSession(_serverAddress, _sessionID, vault.localPartyID)
            }
            _participantDiscovery?.discoveryParticipants()
        }
    }

    private fun updateTransactionUiModel(
        keysignPayload: KeysignPayload,
    ) {
        transactionId?.let {
            val isSwap = keysignPayload.swapPayload != null
            val isDeposit = when (val specific = keysignPayload.blockChainSpecific) {
                is BlockChainSpecific.MayaChain -> specific.isDeposit
                is BlockChainSpecific.THORChain -> specific.isDeposit
                else -> false
            }
            viewModelScope.launch {
                transitionTypeUiModel = when {
                    isSwap -> TransitionTypeUiModel.Swap(
                        mapSwapTransactionToUiModel(
                            swapTransactionRepository.getTransaction(transactionId)
                        )
                    )

                    isDeposit -> TransitionTypeUiModel.Deposit(
                        mapDepositTransactionUiModel(
                            depositTransactionRepository.getTransaction(transactionId)
                        )
                    )

                    else -> TransitionTypeUiModel.Send(
                        mapTransactionToUiModel(
                            transactionRepository.getTransaction(
                                transactionId
                            ).first()
                        )
                    )
                }
            }
        }
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Timber.tag("KeysignFlowViewModel").d("onReceive: Mediator service started")
                if (_currentVault == null) {
                    moveToState(KeysignFlowState.Error("Vault is not set"))
                    return
                }
                // send a request to local mediator server to start the session
                GlobalScope.launch(Dispatchers.IO) {
                    delay(1000) // back off a second
                    startSession(_serverAddress, _sessionID, _currentVault!!.localPartyID)
                }
                // kick off discovery
                _participantDiscovery?.discoveryParticipants()
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
            //Todo Handle older Android versions if needed
            context.registerReceiver(serviceStartedReceiver, filter)
        }

        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        intent.putExtra("serverName", _serviceName)
        context.startService(intent)
        Timber.tag("KeysignFlowViewModel").d("startMediatorService: Mediator service started")
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

            if (password != null) {
                val vault = _currentVault!!
                vultiSignerRepository.joinKeysign(
                    JoinKeysignRequestJson(
                        publicKeyEcdsa = vault.pubKeyECDSA,
                        messages = messagesToSign,
                        sessionId = sessionID,
                        hexEncryptionKey = _encryptionKeyHex,
                        derivePath = _keysignPayload!!.coin.coinType.derivationPath(),
                        isEcdsa = _keysignPayload?.coin?.chain?.TssKeysignType == TssKeyType.ECDSA,
                        password = password,
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
            moveToState(KeysignFlowState.Error(e.message.toString()))
        }
    }

    fun stopParticipantDiscovery() = viewModelScope.launch {
        _participantDiscovery?.stop()
    }

    private fun cleanQrAddress() {
        addressProvider.clean()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun changeNetworkPromptOption(option: NetworkPromptOption, context: Context) {
        if (networkOption.value == option) return
        networkOption.value = option
        _serverAddress = when (option) {
            NetworkPromptOption.LOCAL -> {
                "http://127.0.0.1:18080"
            }

            NetworkPromptOption.INTERNET -> {
                Endpoints.VULTISIG_RELAY
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
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

    private fun Coin.toCoinProto() = CoinProto(
        chain = chain.raw,
        ticker = ticker,
        address = address,
        contractAddress = contractAddress,
        decimals = decimal,
        priceProviderId = priceProviderID,
        isNativeToken = isNativeToken,
        hexPublicKey = hexPublicKey,
        logo = logo,
    )

    override fun onCleared() {
        cleanQrAddress()
        stopService(context)
        super.onCleared()
    }

    fun tryAgain() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}