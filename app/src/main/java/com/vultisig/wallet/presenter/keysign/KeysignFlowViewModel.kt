@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.presenter.keysign

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.google.gson.Gson
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.Utils
import com.vultisig.wallet.common.vultisigRelay
import com.vultisig.wallet.common.zipZlibAndBase64Encode
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.SwapPayload
import com.vultisig.wallet.data.models.proto.v1.CoinProto
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.mediator.MediatorService
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.ERC20ApprovePayload
import com.vultisig.wallet.models.TssKeysignType
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.keygen.NetworkPromptOption
import com.vultisig.wallet.presenter.keygen.ParticipantDiscovery
import com.vultisig.wallet.tss.TssKeyType
import com.vultisig.wallet.ui.models.AddressProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import vultisig.keysign.v1.CosmosSpecific
import vultisig.keysign.v1.Erc20ApprovePayload
import vultisig.keysign.v1.EthereumSpecific
import vultisig.keysign.v1.MAYAChainSpecific
import vultisig.keysign.v1.OneInchQuote
import vultisig.keysign.v1.OneInchSwapPayload
import vultisig.keysign.v1.PolkadotSpecific
import vultisig.keysign.v1.SolanaSpecific
import vultisig.keysign.v1.THORChainSpecific
import vultisig.keysign.v1.THORChainSwapPayload
import vultisig.keysign.v1.UTXOSpecific
import java.net.HttpURLConnection
import java.util.UUID
import javax.inject.Inject
import kotlin.random.Random

enum class KeysignFlowState {
    PEER_DISCOVERY, KEYSIGN, ERROR,
}

@HiltViewModel
internal class KeysignFlowViewModel @Inject constructor(
    private val vultisigRelay: vultisigRelay,
    private val gson: Gson,
    private val protoBuf: ProtoBuf,
    private val thorChainApi: ThorChainApi,
    private val blockChairApi: BlockChairApi,
    private val evmApiFactory: EvmApiFactory,
    private val mayaChainApi: MayaChainApi,
    private val cosmosApiFactory: CosmosApiFactory,
    private val solanaApi: SolanaApi,
    private val polkadotApi: PolkadotApi,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val addressProvider: AddressProvider
) : ViewModel() {
    private val _sessionID: String = UUID.randomUUID().toString()
    private val _serviceName: String = "vultisigApp-${Random.nextInt(1, 1000)}"
    private var _serverAddress: String = "http://127.0.0.1:18080" // local mediator server
    private var _participantDiscovery: ParticipantDiscovery? = null
    private val _encryptionKeyHex: String = Utils.encryptionKeyHex
    private var _currentVault: Vault? = null
    private var _keysignPayload: KeysignPayload? = null
    private val _keysignMessage: MutableState<String> = mutableStateOf("")
    var currentState: MutableState<KeysignFlowState> =
        mutableStateOf(KeysignFlowState.PEER_DISCOVERY)
    var errorMessage: MutableState<String> = mutableStateOf("")
    val selection = MutableLiveData<List<String>>()
    val localPartyID: String?
        get() = _currentVault?.localPartyID
    val keysignMessage: MutableState<String>
        get() = _keysignMessage
    val participants: MutableLiveData<List<String>>
        get() = _participantDiscovery?.participants ?: MutableLiveData(listOf())

    val networkOption: MutableState<NetworkPromptOption> = mutableStateOf(NetworkPromptOption.LOCAL)

    val keysignViewModel: KeysignViewModel
        get() = KeysignViewModel(
            vault = _currentVault!!,
            keysignCommittee = selection.value!!,
            serverAddress = _serverAddress,
            sessionId = _sessionID,
            encryptionKeyHex = _encryptionKeyHex,
            messagesToSign = _keysignPayload!!.getKeysignMessages(_currentVault!!),
            keyType = _keysignPayload?.coin?.chain?.TssKeysignType ?: TssKeyType.ECDSA,
            keysignPayload = _keysignPayload!!,
            gson = gson,
            thorChainApi = thorChainApi,
            blockChairApi = blockChairApi,
            evmApiFactory = evmApiFactory,
            mayaChainApi = mayaChainApi,
            cosmosApiFactory = cosmosApiFactory,
            solanaApi = solanaApi,
            polkadotApi = polkadotApi,
            explorerLinkRepository = explorerLinkRepository,
        )

    suspend fun setData(vault: Vault, context: Context, keysignPayload: KeysignPayload) {
        _currentVault = vault
        _keysignPayload = keysignPayload
        this.selection.value = listOf(vault.localPartyID)
        if (vultisigRelay.IsRelayEnabled) {
            _serverAddress = Endpoints.VULTISIG_RELAY
            networkOption.value = NetworkPromptOption.INTERNET
        }
        updateKeysignPayload(context)
    }

    private suspend fun updateKeysignPayload(context: Context) {
        stopParticipantDiscovery()
        _currentVault ?: run {
            errorMessage.value = "Vault is not set"
            moveToState(KeysignFlowState.ERROR)
            return
        }
        val vault = _currentVault!!
        _participantDiscovery = ParticipantDiscovery(
            _serverAddress,
            _sessionID,
            vault.localPartyID,
            gson
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
                        vultisig.keysign.v1.UtxoInfo(
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
                        )
                    } else null,
                    mayaSpecific = if (specific is BlockChainSpecific.MayaChain) {
                        MAYAChainSpecific(
                            accountNumber = specific.accountNumber.toString().toULong(),
                            sequence = specific.sequence.toString().toULong(),
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
                    suicheSpecific = null, // TODO add sui chain
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
                            streamingInterval = from.steamingInterval,
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
                            streamingInterval = from.steamingInterval,
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
                            quote = from.quote.let {
                                OneInchQuote(
                                    dstAmount = it.dstAmount.toString(),
                                    tx = it.tx.let {
                                        vultisig.keysign.v1.OneInchTransaction(
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
                useVultisigRelay = vultisigRelay.IsRelayEnabled
            )
        )

        Timber.d("keysignProto: $keysignProto")

        _keysignMessage.value =
            "vultisig://vultisig.com?type=SignTransaction&resharePrefix=${vault.resharePrefix}&vault=${vault.pubKeyECDSA}&jsonData=" +
                    keysignProto.zipZlibAndBase64Encode()
        addressProvider.update(_keysignMessage.value)
        if (!vultisigRelay.IsRelayEnabled) {
            startMediatorService(context)
        } else {
            _serverAddress = Endpoints.VULTISIG_RELAY
            withContext(Dispatchers.IO) {
                startSession(_serverAddress, _sessionID, vault.localPartyID)
            }
            _participantDiscovery?.discoveryParticipants()
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private val serviceStartedReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == MediatorService.SERVICE_ACTION) {
                Timber.tag("KeysignFlowViewModel").d("onReceive: Mediator service started")
                if (_currentVault == null) {
                    errorMessage.value = "Vault is not set"
                    moveToState(KeysignFlowState.ERROR)
                    return
                }
                // send a request to local mediator server to start the session
                GlobalScope.launch(Dispatchers.IO) {
                    Thread.sleep(1000) // back off a second
                    startSession(_serverAddress, _sessionID, _currentVault!!.localPartyID)
                }
                // kick off discovery
                _participantDiscovery?.discoveryParticipants()
            }
        }
    }

    fun stopService(context: Context) {
        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        context.stopService(intent)
        Timber.d("stopService: Mediator service stopped")

    }

    private fun startMediatorService(context: Context) {
        val filter = IntentFilter()
        filter.addAction(MediatorService.SERVICE_ACTION)
        context.registerReceiver(serviceStartedReceiver, filter, Context.RECEIVER_EXPORTED)

        // start mediator service
        val intent = Intent(context, MediatorService::class.java)
        intent.putExtra("serverName", _serviceName)
        context.startService(intent)
        Timber.tag("KeysignFlowViewModel").d("startMediatorService: Mediator service started")
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
                        Timber.tag("KeysignFlowViewModel").d("startSession: Session started")
                    }

                    else -> Timber.tag("KeysignFlowViewModel").d(
                        "startSession: Response code: ${response.code}"
                    )
                }
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

    fun moveToState(nextState: KeysignFlowState) {
        currentState.value = nextState
    }

    fun stopParticipantDiscovery() {
        _participantDiscovery?.stop()
    }

    fun resetQrAddress(){
        addressProvider.clean()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun changeNetworkPromptOption(option: NetworkPromptOption, context: Context) {
        if (networkOption.value == option) return
        when (option) {
            NetworkPromptOption.LOCAL -> {
                vultisigRelay.IsRelayEnabled = false
                _serverAddress = "http://127.0.0.1:18080"
                networkOption.value = option
            }

            NetworkPromptOption.INTERNET -> {
                vultisigRelay.IsRelayEnabled = true
                _serverAddress = Endpoints.VULTISIG_RELAY
                networkOption.value = option
            }
        }

        GlobalScope.launch(Dispatchers.IO) {
            updateKeysignPayload(context)
        }
    }

    suspend fun startKeysign() {
        withContext(Dispatchers.IO) {
            try {
                val keygenCommittee = selection.value ?: emptyList()
                val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
                val payload = gson.toJson(keygenCommittee)
                val request = okhttp3.Request.Builder().url("$_serverAddress/start/$_sessionID")
                    .post(payload.toRequestBody("application/json".toMediaType())).build()
                client.newCall(request).execute().use { response ->
                    if (response.code == HttpURLConnection.HTTP_OK) {
                        Timber.d("Keysign started")
                    } else {
                        Timber.e("Fail to start keysign: Response code: ${response.code}")

                    }
                }
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

}