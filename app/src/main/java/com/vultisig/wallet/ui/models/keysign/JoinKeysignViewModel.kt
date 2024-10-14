@file:OptIn(ExperimentalSerializationApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.net.nsd.NsdManager
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.BlockChairApi
import com.vultisig.wallet.data.api.CosmosApiFactory
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.PolkadotApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.SolanaApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.chains.SuiApi
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.crypto.ThorChainHelper
import com.vultisig.wallet.data.mappers.KeysignMessageFromProtoMapper
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BlowfishRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.DecompressQrUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.deposit.VerifyDepositUiModel
import com.vultisig.wallet.ui.models.keygen.MediatorServiceDiscoveryListener
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.swap.SwapFormViewModel.Companion.AFFILIATE_FEE_USD_THRESHOLD
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import vultisig.keysign.v1.KeysignMessage
import java.math.BigInteger
import java.util.UUID
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

sealed class JoinKeysignError(val message: UiText) {
    data class FailedToCheck(val exceptionMessage: String) :
        JoinKeysignError(UiText.DynamicString(exceptionMessage))

    data object WrongVault : JoinKeysignError(R.string.join_keysign_wrong_vault.asUiText())
    data object WrongReShare : JoinKeysignError(R.string.join_keysign_wrong_reshare.asUiText())
    data object InvalidQr : JoinKeysignError(R.string.join_keysign_invalid_qr.asUiText())
    data object FailedToStart : JoinKeysignError(R.string.join_keysign_failed_to_start.asUiText())
}

sealed interface JoinKeysignState {
    data object DiscoveringSessionID : JoinKeysignState
    data object DiscoverService : JoinKeysignState
    data object JoinKeysign : JoinKeysignState
    data object WaitingForKeysignStart : JoinKeysignState
    data object Keysign : JoinKeysignState
    data class Error(val errorType: JoinKeysignError) : JoinKeysignState
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
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val tokenRepository: TokenRepository,
    private val gasFeeRepository: GasFeeRepository,
    private val swapQuoteRepository: SwapQuoteRepository,
    private val blowfishRepository: BlowfishRepository,
    private val vaultRepository: VaultRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val mapKeysignMessageFromProto: KeysignMessageFromProtoMapper,
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
    private val decompressQr: DecompressQrUseCase,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
) : ViewModel() {
    val vaultId: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_ID])
    private val qrBase64: String = requireNotNull(savedStateHandle[Destination.ARG_QR])
    private var _currentVault: Vault = Vault(id = UUID.randomUUID().toString(), "temp vault")
    var currentState: MutableState<JoinKeysignState> =
        mutableStateOf(JoinKeysignState.DiscoveringSessionID)
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
    private var messagesToSign: List<String> = emptyList()

    private var _jobWaitingForKeysignStart: Job? = null
    private var isNavigateToHome: Boolean = false

    private var transitionTypeUiModel: TransitionTypeUiModel? = null

    private val keysignPayload: KeysignPayload?
        get() = _keysignPayload
    val keysignViewModel: KeysignViewModel
        get() = KeysignViewModel(
            vault = _currentVault,
            keysignCommittee = _keysignCommittee,
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
            transitionTypeUiModel = transitionTypeUiModel,
            encryption = encryption,
            featureFlagApi = featureFlagApi,
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
                    val matchingVault = vaultRepository.getAll().firstOrNull {
                        it.pubKeyECDSA == payload.payload.vaultPublicKeyECDSA
                    }
                    matchingVault?.let {
                        _currentVault = it
                        _localPartyID = it.localPartyID
                    } ?: run {
                        currentState.value = JoinKeysignState.Error(JoinKeysignError.WrongVault)
                        return@launch
                    }
                }
                val deepLink = DeepLinkHelper(content)
                if (deepLink.hasResharePrefix()) {
                    if (_currentVault.resharePrefix != deepLink.getResharePrefix()) {
                        currentState.value = JoinKeysignState.Error(JoinKeysignError.WrongReShare)
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
                currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
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

                val nativeToken = tokenRepository.getNativeToken(dstToken.chain.id)

                when (swapPayload) {
                    is SwapPayload.OneInch -> {
                        val estimatedTokenFees = TokenValue(
                            value = swapPayload.data.quote.tx.gasPrice.toBigInteger() *
                                    (swapPayload.data.quote.tx.gas.takeIf { it != 0L }
                                        ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT).toBigInteger(),
                            token = nativeToken
                        )

                        val swapTransaction = SwapTransactionUiModel(
                            srcTokenValue = mapTokenValueToStringWithUnit(srcTokenValue),
                            dstTokenValue = mapTokenValueToStringWithUnit(dstTokenValue),
                            estimatedFees = fiatValueToStringMapper.map(
                                convertTokenValueToFiat(
                                    nativeToken,
                                    estimatedTokenFees,
                                    currency
                                )
                            ),
                        )

                        transitionTypeUiModel = TransitionTypeUiModel.Swap(swapTransaction)

                        verifyUiModel.value = VerifyUiModel.Swap(
                            VerifySwapUiModel(
                                provider = R.string.swap_for_provider_1inch.asUiText(),
                                swapTransactionUiModel = swapTransaction
                            )
                        )
                    }

                    is SwapPayload.ThorChain -> {
                        val srcUsdFiatValue = convertTokenValueToFiat(
                            srcToken, srcTokenValue, AppCurrency.USD,
                        )

                        val isAffiliate =
                            srcUsdFiatValue.value >= AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()

                        val quote = swapQuoteRepository.getSwapQuote(
                            srcToken = srcToken,
                            dstToken = dstToken,
                            dstAddress = swapPayload.data.toAddress,
                            tokenValue = srcTokenValue,
                            isAffiliate = isAffiliate,
                        )

                        val swapTransactionUiModel = SwapTransactionUiModel(
                            srcTokenValue = mapTokenValueToStringWithUnit(srcTokenValue),
                            dstTokenValue = mapTokenValueToStringWithUnit(dstTokenValue),
                            estimatedFees = fiatValueToStringMapper.map(
                                convertTokenValueToFiat(dstToken, quote.fees, currency)
                            ),
                        )
                        transitionTypeUiModel = TransitionTypeUiModel.Swap(swapTransactionUiModel)
                        verifyUiModel.value = VerifyUiModel.Swap(
                            VerifySwapUiModel(
                                provider = R.string.swap_form_provider_thorchain.asUiText(),
                                swapTransactionUiModel = swapTransactionUiModel
                            )
                        )
                    }

                    is SwapPayload.MayaChain -> {
                        val srcUsdFiatValue = convertTokenValueToFiat(
                            srcToken, srcTokenValue, AppCurrency.USD,
                        )

                        val isAffiliate =
                            srcUsdFiatValue.value >= AFFILIATE_FEE_USD_THRESHOLD.toBigDecimal()


                        val quote = swapQuoteRepository.getMayaSwapQuote(
                            srcToken = srcToken,
                            dstToken = dstToken,
                            dstAddress = swapPayload.data.toAddress,
                            tokenValue = srcTokenValue,
                            isAffiliate = isAffiliate
                        )

                        val swapTransactionUiModel = SwapTransactionUiModel(
                            srcTokenValue = mapTokenValueToStringWithUnit(srcTokenValue),
                            dstTokenValue = mapTokenValueToStringWithUnit(dstTokenValue),
                            estimatedFees = fiatValueToStringMapper.map(
                                convertTokenValueToFiat(nativeToken, quote.fees, currency)
                            ),
                        )
                        transitionTypeUiModel = TransitionTypeUiModel.Swap(swapTransactionUiModel)
                        verifyUiModel.value = VerifyUiModel.Swap(
                            VerifySwapUiModel(
                                provider = R.string.swap_form_provider_mayachain.asUiText(),
                                swapTransactionUiModel = swapTransactionUiModel
                            )
                        )
                    }
                }
            }
            else -> {
                val isDeposit = when (val specific = payload.blockChainSpecific) {
                    is BlockChainSpecific.MayaChain -> specific.isDeposit
                    is BlockChainSpecific.THORChain -> specific.isDeposit
                    else -> false
                }

                if (isDeposit) {
                    val fee = when (val specific = payload.blockChainSpecific) {
                        is BlockChainSpecific.MayaChain -> ThorChainHelper.MAYA_CHAIN_GAS_UNIT.toBigInteger()
                        is BlockChainSpecific.THORChain -> specific.fee
                        else -> error("BlockChainSpecific $specific is not supported")
                    }

                    val depositTransactionUiModel = DepositTransactionUiModel(
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
                        memo = payload.memo ?: "",
                    )
                    transitionTypeUiModel = TransitionTypeUiModel.Deposit(depositTransactionUiModel)
                    verifyUiModel.value = VerifyUiModel.Deposit(
                        VerifyDepositUiModel(
                            depositTransactionUiModel
                        )
                    )
                } else {
                    val payloadToken = payload.coin
                    val address = payloadToken.address
                    val chain = payloadToken.chain

                    val tokenValue = TokenValue(
                        value = payload.toAmount,
                        unit = payloadToken.ticker,
                        decimals = payloadToken.decimal,
                    )

                    val gasFee = gasFeeRepository.getGasFee(chain, address)
                    val totalGasAndFee = gasFeeToEstimatedFee(
                        GasFeeParams(
                            gasLimit = if (chain.standard == TokenStandard.EVM) {
                                (payload.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
                            } else {
                                BigInteger.valueOf(1)
                            },
                            gasFee = gasFee,
                            selectedToken = payload.coin,
                        )
                    )

                    val transaction = Transaction(
                        id = UUID.randomUUID().toString(),

                        vaultId = payload.vaultPublicKeyECDSA,
                        chainId = chain.id,
                        tokenId = payloadToken.id,
                        srcAddress = address,
                        dstAddress = payload.toAddress,
                        tokenValue = tokenValue,
                        fiatValue = convertTokenValueToFiat(
                            payloadToken,
                            tokenValue,
                            currency,
                        ),
                        gasFee = gasFee,
                        memo = payload.memo,
                        estimatedFee = totalGasAndFee.first,
                        blockChainSpecific = payload.blockChainSpecific,
                        totalGass = totalGasAndFee.second
                    )

                    val transactionToUiModel = mapTransactionToUiModel(transaction)
                    transitionTypeUiModel = TransitionTypeUiModel.Send(transactionToUiModel)
                    verifyUiModel.value = VerifyUiModel.Send(
                        VerifyTransactionUiModel(
                            transaction = transactionToUiModel,
                        )
                    )
                    blowfishTransactionScan(transaction)
                }
            }
        }
    }

    private fun onServerAddressDiscovered(address: String) {
        _serverAddress = address
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
                    Timber.tag("JoinKeysignViewModel").d("Joining keysign")
                    sessionApi.startSession(_serverAddress, _sessionID, listOf(_localPartyID))
                    waitForKeysignToStart()
                    currentState.value = JoinKeysignState.WaitingForKeysignStart
                } catch (e: Exception) {
                    Timber.tag("JoinKeysignViewModel")
                        .e("Failed to join keysign: %s", e.stackTraceToString())
                    currentState.value = JoinKeysignState.Error(JoinKeysignError.FailedToStart)
                }
            }
        }
    }

    fun tryAgain() {
        viewModelScope.launch {
            val keysignError = currentState.value as JoinKeysignState.Error
            when (keysignError.errorType) {
                JoinKeysignError.WrongVault -> navigator.navigate(
                    Destination.Home(showVaultList = true),
                    opts = NavigationOptions(clearBackStack = true)
                )

                else -> navigator.navigate(Destination.Back)
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
                    delay(1000)
                }
            }
        }
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    private suspend fun checkKeygenStarted(): Boolean {
        try {
            this._keysignCommittee = sessionApi.checkCommittee(_serverAddress, _sessionID)
            Timber.d("Keysign committee: $_keysignCommittee")
            Timber.d("local party: $_localPartyID")
            if (this._keysignCommittee.contains(_localPartyID)) {
                this.messagesToSign = SigningHelper.getKeysignMessages(
                    payload = keysignPayload!!,
                    vault = _currentVault,
                )
                return true
            }
        } catch (e: Exception) {
            Timber.e(
                "Failed to check keysign start: ${e.stackTraceToString()}"
            )
            currentState.value =
                JoinKeysignState.Error(JoinKeysignError.FailedToCheck(e.message.toString()))
        }
        return false
    }
    fun enableNavigationToHome() {
        isNavigateToHome = true
    }

    fun navigateToHome() {
        viewModelScope.launch {
            if (isNavigateToHome) {
                navigator.navigate(
                    Destination.Home(),
                    NavigationOptions(
                        clearBackStack = true
                    )
                )
            } else {
                navigator.navigate(Destination.Back)
            }
        }
    }

    override fun onCleared() {
        cleanUp()
        super.onCleared()
    }

    private fun blowfishTransactionScan(transaction: Transaction) {
        viewModelScope.launch {
            val vault = requireNotNull(vaultRepository.get(vaultId))

            try {
                val result = blowfishRepository.scanBlowfishTransaction(vault, transaction)
                verifyUiModel.update { state ->
                    (state as VerifyUiModel.Send).copy(
                        model = state.model.copy(
                            blowfishShow = result.first,
                            blowfishWarnings = result.second
                        )
                    )
                }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }
}