@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.presenter.keysign

import android.net.nsd.NsdManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.vultisig.wallet.R
import com.vultisig.wallet.chains.EvmHelper
import com.vultisig.wallet.chains.MayaChainHelper
import com.vultisig.wallet.common.DeepLinkHelper
import com.vultisig.wallet.common.Endpoints
import com.vultisig.wallet.common.asUiText
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.mappers.KeysignMessageFromProtoMapper
import com.vultisig.wallet.data.models.SwapPayload
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ConvertTokenAndValueToTokenValueUseCase
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.DecompressQrUseCase
import com.vultisig.wallet.models.TssKeysignType
import com.vultisig.wallet.models.Vault
import com.vultisig.wallet.presenter.keygen.MediatorServiceDiscoveryListener
import com.vultisig.wallet.tss.TssKeyType
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.deposit.VerifyDepositUiModel
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import vultisig.keysign.v1.KeysignMessage
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

enum class JoinKeysignState {
    DiscoveryingSessionID, DiscoverService, JoinKeysign, WaitingForKeysignStart, Keysign, FailedToStart, Error
}

internal sealed class VerifyUiModel {

    data class Send(
        val model: VerifyTransactionUiModel,
    ) : VerifyUiModel()

    data class Swap(
        val model: VerifySwapUiModel,
    ) : VerifyUiModel()

    data class Deposit(
        val model: VerifyDepositUiModel,
    ) : VerifyUiModel()

}

@HiltViewModel
internal class JoinKeysignViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,

    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val convertTokenAndValueToTokenValue: ConvertTokenAndValueToTokenValueUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val tokenRepository: TokenRepository,
    private val gasFeeRepository: GasFeeRepository,
    private val swapQuoteRepository: SwapQuoteRepository,

    private val vaultRepository: VaultRepository,
    private val mapKeysignMessageFromProto: KeysignMessageFromProtoMapper,
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
    private val decompressQr: DecompressQrUseCase,
) : ViewModel() {
    val vaultId: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_ID])
    private val qrBase64: String = requireNotNull(savedStateHandle[Destination.ARG_QR])
    private var _currentVault: Vault = Vault(id = UUID.randomUUID().toString(), "temp vault")
    var currentState: MutableState<JoinKeysignState> =
        mutableStateOf(JoinKeysignState.DiscoveryingSessionID)
    var errorMessage: MutableState<String> = mutableStateOf("")
    private var _localPartyID: String = ""
    private var _sessionID: String = ""
    private var _serviceName: String = ""
    private var _useVultisigRelay: Boolean = false
    private var _encryptionKeyHex: String = ""
    private var _serverAddress: String = ""
    private var _keysignCommittee: List<String> = emptyList()
    private var _discoveryListener: MediatorServiceDiscoveryListener? = null
    private var _nsdManager: NsdManager? = null
    private var _keysignPayload: KeysignPayload? = null
    private var _jobWaitingForKeysignStart: Job? = null

    val keysignPayload: KeysignPayload?
        get() = _keysignPayload
    val keysignViewModel: KeysignViewModel
        get() = KeysignViewModel(
            vault = _currentVault,
            keysignCommittee = _keysignCommittee,
            serverAddress = _serverAddress,
            sessionId = _sessionID,
            encryptionKeyHex = _encryptionKeyHex,
            messagesToSign = _keysignPayload!!.getKeysignMessages(_currentVault),
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

    val verifyUiModel =
        MutableStateFlow<VerifyUiModel>(VerifyUiModel.Send(VerifyTransactionUiModel()))

    init {
        setScanResult(qrBase64)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun setScanResult(qrBase64: String) {
        viewModelScope.launch {
            vaultRepository.get(vaultId)?.let {
                _currentVault = it
                _localPartyID = it.localPartyID
            }

            try {
                val content = Base64.UrlSafe.decode(qrBase64.toByteArray())
                    .decodeToString()

                val qrCodeContent = DeepLinkHelper(content).getJsonData()
                qrCodeContent ?: run {
                    throw Exception("Invalid QR code content")
                }
                val rawJson = decompressQr(qrCodeContent.decodeBase64Bytes())

                val payloadProto = protoBuf.decodeFromByteArray<KeysignMessage>(rawJson)
                Timber.d("Decoded KeysignMessageProto: $payloadProto")

                val payload = mapKeysignMessageFromProto(payloadProto)
                Timber.d("Mapped proto to KeysignMessage: $payload")

                if (_currentVault.pubKeyECDSA != payload.payload.vaultPublicKeyECDSA) {
                    errorMessage.value = "Wrong vault"
                    currentState.value = JoinKeysignState.Error
                    return@launch
                }
                val deepLink = DeepLinkHelper(content)
                if (deepLink.hasResharePrefix()) {
                    if (_currentVault.resharePrefix != deepLink.getResharePrefix()) {
                        errorMessage.value = R.string.join_keysign_wrong_reshare.asUiText().toString()
                        currentState.value = JoinKeysignState.Error
                        return@launch
                    }
                }
                val ksPayload = payload.payload
                this@JoinKeysignViewModel._keysignPayload = ksPayload
                this@JoinKeysignViewModel._sessionID = payload.sessionID
                this@JoinKeysignViewModel._serviceName = payload.serviceName
                this@JoinKeysignViewModel._useVultisigRelay = payload.useVultisigRelay
                this@JoinKeysignViewModel._encryptionKeyHex = payload.encryptionKeyHex

                loadTransaction(ksPayload)

                if (_useVultisigRelay) {
                    this@JoinKeysignViewModel._serverAddress = Endpoints.VULTISIG_RELAY
                    currentState.value = JoinKeysignState.JoinKeysign
                } else {
                    currentState.value = JoinKeysignState.DiscoverService
                }
            } catch (e: Exception) {
                Timber.d(e, "Failed to parse QR code")
                errorMessage.value = "Invalid QR code content"
                currentState.value = JoinKeysignState.Error
            }
        }
    }

    private suspend fun loadTransaction(payload: KeysignPayload) {
        val swapPayload = payload.swapPayload
        val currency = appCurrencyRepository.currency.first()

        when {
            swapPayload != null -> {
                val srcToken = swapPayload.srcToken
                val dstToken = swapPayload.dstToken

                val srcTokenValue = swapPayload.srcTokenValue
                val dstTokenValue = swapPayload.dstTokenValue

                val nativeToken = tokenRepository.getNativeToken(srcToken.chain.id)

                when (swapPayload) {
                    is SwapPayload.OneInch -> {
                        val estimatedTokenFees = TokenValue(
                            value = swapPayload.data.quote.tx.gasPrice.toBigInteger() *
                                    EvmHelper.DefaultEthSwapGasUnit.toBigInteger(),
                            token = nativeToken
                        )

                        verifyUiModel.value = VerifyUiModel.Swap(
                            VerifySwapUiModel(
                                provider = R.string.swap_for_provider_1inch.asUiText(),
                                srcTokenValue = mapTokenValueToStringWithUnit(srcTokenValue),
                                dstTokenValue = mapTokenValueToStringWithUnit(dstTokenValue),
                                estimatedFees = fiatValueToStringMapper.map(
                                    convertTokenValueToFiat(nativeToken, estimatedTokenFees, currency)
                                ),
                            )
                        )
                    }

                    is SwapPayload.ThorChain -> {
                        val quote = swapQuoteRepository.getSwapQuote(
                            srcToken = srcToken,
                            dstToken = dstToken,
                            dstAddress = swapPayload.data.toAddress,
                            tokenValue = srcTokenValue,
                        )

                        verifyUiModel.value = VerifyUiModel.Swap(
                            VerifySwapUiModel(
                                provider = R.string.swap_form_provider_thorchain.asUiText(),
                                srcTokenValue = mapTokenValueToStringWithUnit(srcTokenValue),
                                dstTokenValue = mapTokenValueToStringWithUnit(dstTokenValue),
                                estimatedFees = fiatValueToStringMapper.map(
                                    convertTokenValueToFiat(nativeToken, quote.fees, currency)
                                ),
                            )
                        )
                    }

                    is SwapPayload.MayaChain -> {
                        val quote = swapQuoteRepository.getMayaSwapQuote(
                            srcToken = srcToken,
                            dstToken = dstToken,
                            dstAddress = swapPayload.data.toAddress,
                            tokenValue = srcTokenValue,
                        )

                        verifyUiModel.value = VerifyUiModel.Swap(
                            VerifySwapUiModel(
                                provider = R.string.swap_form_provider_mayachain.asUiText(),
                                srcTokenValue = mapTokenValueToStringWithUnit(srcTokenValue),
                                dstTokenValue = mapTokenValueToStringWithUnit(dstTokenValue),
                                estimatedFees = fiatValueToStringMapper.map(
                                    convertTokenValueToFiat(nativeToken, quote.fees, currency)
                                ),
                            )
                        )
                    }
                }
            }
            else -> {
                val memo = payload.memo
                val isDeposit = !memo.isNullOrEmpty() && MayaChainHelper.DEPOSIT_PREFIXES.any { memo.startsWith(it) }

                if (isDeposit) {
                    val fee = when (val specific = payload.blockChainSpecific) {
                        is BlockChainSpecific.MayaChain -> MayaChainHelper.MayaChainGasUnit.toBigInteger()
                        is BlockChainSpecific.THORChain -> specific.fee
                        else -> error("BlockChainSpecific $specific is not supported")
                    }

                    verifyUiModel.value = VerifyUiModel.Deposit(
                        VerifyDepositUiModel(
                            fromAddress = payload.coin.address,
                            // TODO toAddress is empty on ios, get node address from memo
                            nodeAddress = payload.toAddress,
                            srcTokenValue = mapTokenValueToStringWithUnit(
                                TokenValue(
                                    value = payload.toAmount,
                                    token = payload.coin,
                                )

                            ),
                            estimatedFees = mapTokenValueToStringWithUnit(
                                TokenValue(
                                    value = fee,
                                    token = payload.coin,
                                )
                            ),
                            memo = memo ?: "",
                        )
                    )
                } else {
                    val payloadToken = payload.coin
                    val address = payloadToken.address
                    val token = tokenRepository.getToken(payloadToken.id)!!
                    val chain = token.chain

                    val tokenValue = TokenValue(
                        value = payload.toAmount,
                        unit = token.ticker,
                        decimals = token.decimal,
                    )

                    val gasFee = gasFeeRepository.getGasFee(chain, address)

                    val transaction = Transaction(
                        id = UUID.randomUUID().toString(),

                        vaultId = payload.vaultPublicKeyECDSA,
                        chainId = chain.id,
                        tokenId = token.id,
                        srcAddress = address,
                        dstAddress = payload.toAddress,
                        tokenValue = tokenValue,
                        fiatValue = convertTokenValueToFiat(
                            token,
                            tokenValue,
                            currency,
                        ),
                        gasFee = gasFee,
                        memo = payload.memo,

                        // TODO that's mock data
                        blockChainSpecific = BlockChainSpecific.THORChain(
                            BigInteger.ZERO,
                            BigInteger.ZERO,
                            BigInteger.ZERO
                        ),
                    )

                    verifyUiModel.value = VerifyUiModel.Send(
                        VerifyTransactionUiModel(
                            transaction = mapTransactionToUiModel(transaction),
                        )
                    )
                }
            }
        }
    }

    private fun onServerAddressDiscovered(addr: String) {
        _serverAddress = addr
        currentState.value = JoinKeysignState.JoinKeysign
        // discovery finished
        _discoveryListener?.let { _nsdManager?.stopServiceDiscovery(it) }
    }

    fun discoveryMediator(nsdManager: NsdManager) {
        _discoveryListener =
            MediatorServiceDiscoveryListener(nsdManager, _serviceName, ::onServerAddressDiscovered)
        _nsdManager = nsdManager
        nsdManager.discoverServices(
            "_http._tcp.", NsdManager.PROTOCOL_DNS_SD, _discoveryListener
        )
    }

    fun joinKeysign() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    val serverUrl = URL("${_serverAddress}/$_sessionID")
                    Timber.tag("JoinKeysignViewModel").d("Joining keysign at $serverUrl")
                    val payload = listOf(_localPartyID)

                    val client = OkHttpClient().newBuilder().retryOnConnectionFailure(true).build()
                    val request = okhttp3.Request.Builder().method(
                        "POST", gson.toJson(payload).toRequestBody("application/json".toMediaType())
                    ).url(serverUrl).build()
                    client.newCall(request).execute().use {
                        Timber.tag("JoinKeysignViewModel")
                            .d("Join keysign: Response code: %s", it.code)
                    }
                    waitForKeysignToStart()
                    currentState.value = JoinKeysignState.WaitingForKeysignStart
                } catch (e: Exception) {
                    Timber.tag("JoinKeysignViewModel")
                        .e("Failed to join keysign: %s", e.stackTraceToString())
                    errorMessage.value = "Failed to join keysign"
                    currentState.value = JoinKeysignState.FailedToStart
                }
            }
        }
    }

    private fun cleanUp() {
        _jobWaitingForKeysignStart?.cancel()
    }

    private fun waitForKeysignToStart() {
        _jobWaitingForKeysignStart = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                while (isActive) {
                    if (checkKeygenStarted()) {
                        currentState.value = JoinKeysignState.Keysign
                        return@withContext
                    }
                    // backoff 1s
                    Thread.sleep(1000)
                }
            }
        }
    }

    private fun checkKeygenStarted(): Boolean {
        try {
            val serverURL = "$_serverAddress/start/$_sessionID"
            Timber.tag("JoinKeysignViewModel").d("Checking keysign start at %s", serverURL)
            val client = OkHttpClient.Builder().retryOnConnectionFailure(true).build()
            val request = okhttp3.Request.Builder().url(serverURL).get().build()
            client.newCall(request).execute().use { response ->
                when (response.code) {
                    HttpURLConnection.HTTP_OK -> {
                        Timber.d("Keysign started")
                        response.body?.let {
                            val result = it.string()
                            val tokenType = object : TypeToken<List<String>>() {}.type
                            this._keysignCommittee = gson.fromJson(result, tokenType)
                            Timber.d("Keysign committee: $_keysignCommittee")
                            Timber.d("local party: $_localPartyID")
                            if (this._keysignCommittee.contains(_localPartyID)) {
                                return true
                            }
                        }
                    }

                    else -> {
                        Timber.d("Failed to check start keysign: Response code: ${response.code}")
                    }
                }
            }
        } catch (e: Exception) {
            Timber.e(
                "Failed to check keysign start: ${e.stackTraceToString()}"
            )
        }
        return false
    }

    override fun onCleared() {
        cleanUp()
        super.onCleared()
    }
}