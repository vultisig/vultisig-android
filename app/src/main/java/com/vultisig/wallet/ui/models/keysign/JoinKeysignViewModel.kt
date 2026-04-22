@file:OptIn(ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.net.nsd.NsdManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.LiFiChainApi
import com.vultisig.wallet.data.api.RouterApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.utils.HttpException
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Swap
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.EvmHelper
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.normalizeMessageFormat
import com.vultisig.wallet.data.mappers.KeysignMessageFromProtoMapper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.isSecuredAssetEligible
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.SwapPayload
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.FourByteRepository
import com.vultisig.wallet.data.repositories.PrettyJson
import com.vultisig.wallet.data.repositories.SwapQuoteRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.securityscanner.isChainSupported
import com.vultisig.wallet.data.usecases.ConvertTokenValueToFiatUseCase
import com.vultisig.wallet.data.usecases.DecompressQrUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import com.vultisig.wallet.data.usecases.PersistTonConnectSessionUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.ResolveProviderUseCase
import com.vultisig.wallet.data.usecases.resolveprovider.SwapSelectionContext
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.deposit.VerifyDepositUiModel
import com.vultisig.wallet.ui.models.keygen.MediatorServiceDiscoveryListener
import com.vultisig.wallet.ui.models.mappers.DepositTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.SendTransactionHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.SwapTransactionToHistoryDataMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.models.sign.VerifySignMessageUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.normalizeAddressForLookup
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.util.decodeBase64Bytes
import java.math.BigInteger
import java.net.UnknownHostException
import java.util.UUID
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import vultisig.keysign.v1.CustomMessagePayload
import vultisig.keysign.v1.KeysignMessage
import wallet.core.jni.proto.Common.SigningError

sealed class JoinKeysignError(val message: UiText) {
    data class FailedToCheck(val exceptionMessage: String) :
        JoinKeysignError(UiText.DynamicString(exceptionMessage))

    data object MissingRequiredVault :
        JoinKeysignError(R.string.join_keysign_missing_required_vault.asUiText())

    data object WrongVault : JoinKeysignError(R.string.join_keysign_wrong_vault.asUiText())

    data object WrongVaultShare :
        JoinKeysignError(R.string.join_keysign_error_wrong_vault_share.asUiText())

    data object WrongReShare : JoinKeysignError(R.string.join_keysign_wrong_reshare.asUiText())

    data object InvalidQr : JoinKeysignError(R.string.join_keysign_invalid_qr.asUiText())

    data class FailedToStart(val exceptionMessage: String) :
        JoinKeysignError(UiText.DynamicString(exceptionMessage))

    data object FailedConnectToServer :
        JoinKeysignError(R.string.join_keysign_failed_connect_to_server.asUiText())

    data object WrongLibType :
        JoinKeysignError(UiText.StringResource(R.string.join_key_sign_wrong_signing_library_type))

    /** Relay server is unavailable after exhausting all retry attempts. */
    data object RelayUnavailable :
        JoinKeysignError(R.string.join_keysign_relay_unavailable.asUiText())
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

    data class Send(val model: VerifyTransactionUiModel) : VerifyUiModel()

    data class Swap(val model: VerifySwapUiModel) : VerifyUiModel()

    data class Deposit(val model: VerifyDepositUiModel) : VerifyUiModel()

    data class SignMessage(val model: VerifySignMessageUiModel) : VerifyUiModel()
}

internal data class FunctionInfo(val signature: String, val inputs: String)

@HiltViewModel
internal class JoinKeysignViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,
    @param:PrettyJson private val json: Json,
    private val convertTokenValueToFiat: ConvertTokenValueToFiatUseCase,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTransactionHistoryData: SendTransactionHistoryDataMapper,
    private val mapSwapTransactionToHistoryData: SwapTransactionToHistoryDataMapper,
    private val mapDepositTransactionHistoryData: DepositTransactionHistoryDataMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val tokenRepository: TokenRepository,
    private val swapQuoteRepository: SwapQuoteRepository,
    private val vaultRepository: VaultRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val mapKeysignMessageFromProto: KeysignMessageFromProtoMapper,
    private val protoBuf: ProtoBuf,
    private val decompressQr: DecompressQrUseCase,
    private val persistTonConnectSession: PersistTonConnectSessionUseCase,
    private val sessionApi: SessionApi,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val routerApi: RouterApi,
    private val fourByteRepository: FourByteRepository,
    private val securityScannerService: SecurityScannerContract,
    private val addressBookRepository: AddressBookRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val parseCosmosMessage: ParseCosmosMessageUseCase,
    private val resolveProviderUseCase: ResolveProviderUseCase,
    private val keysignViewModelFactory: KeysignViewModel.Factory,
) : ViewModel() {
    companion object {
        private const val VAULT_PARAMETER = "vault"

        private const val ETH_SIGN_TYPED_DATA_V4 = "eth_signTypedData_v4"
    }

    private val args = savedStateHandle.toRoute<Route.Keysign.Join>()
    private val vaultId: String = args.vaultId
    private val qrBase64: String = args.qr
    private var _currentVault: Vault = Vault(id = UUID.randomUUID().toString(), "temp vault")
    val currentState: MutableStateFlow<JoinKeysignState> =
        MutableStateFlow(JoinKeysignState.DiscoveringSessionID)
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
    private var customMessagePayload: CustomMessagePayload? = null
    private var messagesToSign: List<String> = emptyList()

    private var _jobWaitingForKeysignStart: Job? = null
    private var isNavigateToHome: Boolean = false

    private var transactionTypeUiModel: TransactionTypeUiModel? = null
    private var transactionHistoryData: TransactionHistoryData? = null
    private var payloadId: String = ""
    private var customPayloadId: String = ""
    private var tempKeysignMessageProto: KeysignMessageProto? = null

    private val deepLinkHelper = MutableStateFlow<DeepLinkHelper?>(null)

    val keysignViewModel: KeysignViewModel
        get() =
            keysignViewModelFactory.create(
                vault = _currentVault,
                keysignCommittee = _keysignCommittee,
                serverUrl = _serverAddress,
                sessionId = _sessionID,
                encryptionKeyHex = _encryptionKeyHex,
                messagesToSign = messagesToSign,
                keyType = _keysignPayload?.coin?.chain?.TssKeysignType ?: TssKeyType.ECDSA,
                keysignPayload = _keysignPayload,
                customMessagePayload = customMessagePayload,
                transactionTypeUiModel = transactionTypeUiModel,
                isInitiatingDevice = false,
                transactionHistoryData = transactionHistoryData,
            )

    val verifyUiModel =
        MutableStateFlow<VerifyUiModel>(VerifyUiModel.Send(VerifyTransactionUiModel()))

    init {
        setScanResult(qrBase64)
    }

    @OptIn(ExperimentalEncodingApi::class)
    fun setScanResult(qrBase64: String) {
        viewModelScope.launch {
            transactionHistoryData = null
            vaultRepository.get(vaultId)?.let {
                _currentVault = it
                _localPartyID = it.localPartyID
            }

            try {
                val content = Base64.UrlSafe.decode(qrBase64.toByteArray()).decodeToString()

                deepLinkHelper.value = DeepLinkHelper(content)

                val qrCodeContent =
                    requireNotNull(deepLinkHelper.value).getJsonData()
                        ?: error("Invalid QR code content")

                val rawJson = decompressQr(qrCodeContent.decodeBase64Bytes())

                val payloadProto = protoBuf.decodeFromByteArray<KeysignMessage>(rawJson)
                Timber.d("Decoded KeysignMessageProto: $payloadProto")
                runCatching { persistTonConnectSession(payloadProto, vaultId) }
                    .onFailure { Timber.w(it, "Failed to persist TonConnect session") }
                _sessionID = payloadProto.sessionId
                _serviceName = payloadProto.serviceName
                _useVultisigRelay = payloadProto.useVultisigRelay
                _encryptionKeyHex = payloadProto.encryptionKeyHex

                val customMessagePayload = payloadProto.customMessagePayload
                val payloadCustomPayloadId = payloadProto.customPayloadId ?: ""
                if (customMessagePayload != null) {
                    val vaultPublicKeyEcdsa =
                        customMessagePayload.vaultPublicKeyEcdsa.ifEmpty {
                            deepLinkHelper.value?.getParameter(VAULT_PARAMETER) ?: ""
                        }
                    val payloadWithVaultKey =
                        customMessagePayload.copy(vaultPublicKeyEcdsa = vaultPublicKeyEcdsa)
                    if (!handleCustomMessage(payloadWithVaultKey)) {
                        return@launch
                    }
                } else if (payloadCustomPayloadId.isNotEmpty()) {
                    // custom message payload is stored server-side (e.g. signBytes via relay)
                    tempKeysignMessageProto = payloadProto
                    customPayloadId = payloadCustomPayloadId
                } else {
                    // when the payload is in the QRCode
                    if (payloadProto.keysignPayload != null && payloadProto.payloadId.isEmpty()) {
                        if (handleKeysignMessage(payloadProto)) {
                            return@launch
                        }
                    } else {
                        tempKeysignMessageProto = payloadProto
                        payloadId = payloadProto.payloadId
                    }
                }

                if (_useVultisigRelay) {
                    this@JoinKeysignViewModel._serverAddress = Endpoints.VULTISIG_RELAY_URL
                    // when Payload is not in the QRCode
                    if (payloadProto.payloadId.isNotEmpty()) {
                        routerApi.getPayload(_serverAddress, payloadId).let { payload ->
                            if (payload.isNotEmpty()) {
                                val rawPayload = decompressQr(payload.decodeBase64Bytes())
                                val payloadProto =
                                    protoBuf.decodeFromByteArray<KeysignPayloadProto>(rawPayload)
                                val keysignMsgProto =
                                    KeysignMessageProto(
                                        keysignPayload = payloadProto,
                                        sessionId = tempKeysignMessageProto!!.sessionId,
                                        serviceName = tempKeysignMessageProto!!.serviceName,
                                        encryptionKeyHex =
                                            tempKeysignMessageProto!!.encryptionKeyHex,
                                        useVultisigRelay = _useVultisigRelay,
                                        payloadId = payloadId,
                                    )

                                if (handleKeysignMessage(keysignMsgProto)) {
                                    return@launch
                                }
                            }
                        }
                    } else if (payloadCustomPayloadId.isNotEmpty()) {
                        if (!fetchAndHandleCustomMessagePayload(_serverAddress)) {
                            currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
                            return@launch
                        }
                    }
                    currentState.value = JoinKeysignState.JoinKeysign
                } else {
                    currentState.value = JoinKeysignState.DiscoverService
                }
            } catch (e: UnknownHostException) {
                Timber.d(e, "Failed to resolve request")
                currentState.value = JoinKeysignState.Error(JoinKeysignError.FailedConnectToServer)
            } catch (e: Exception) {
                Timber.d(e, "Failed to parse QR code")
                currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
            }
        }
    }

    private suspend fun checkIsVaultCorrect(
        pubKeyEcdsa: String,
        localPartyId: String,
        libType: SigningLibType?,
    ): Boolean {
        if (_currentVault.pubKeyECDSA != pubKeyEcdsa) {
            val matchingVault =
                vaultRepository.getAll().firstOrNull { it.pubKeyECDSA == pubKeyEcdsa }
            if (matchingVault != null) {
                switchToCorrectVault(matchingVault)
                return true
            } else
                currentState.value = JoinKeysignState.Error(JoinKeysignError.MissingRequiredVault)
            return false
        }

        if (localPartyId == _localPartyID) {
            currentState.value = JoinKeysignState.Error(JoinKeysignError.WrongVaultShare)
            return false
        }

        if (libType != null && libType != _currentVault.libType) {
            currentState.value = JoinKeysignState.Error(JoinKeysignError.WrongLibType)
            return false
        }

        return true
    }

    private fun switchToCorrectVault(vault: Vault) {
        _currentVault = vault
        _localPartyID = vault.localPartyID
    }

    private suspend fun handleCustomMessage(customMessage: CustomMessagePayload): Boolean {
        // supports old versions which have no vaultPublicKeyEcdsa or localPartyId
        if (customMessage.vaultPublicKeyEcdsa.isNotEmpty()) {
            if (
                !checkIsVaultCorrect(
                    customMessage.vaultPublicKeyEcdsa,
                    customMessage.vaultLocalPartyId,
                    libType = null,
                )
            ) {
                return false
            }
        }

        customMessagePayload = customMessage

        val model =
            SignMessageTransactionUiModel(
                method = customMessage.method,
                message = getNormalizedCustomMessage(customMessage),
            )

        transactionTypeUiModel = TransactionTypeUiModel.SignMessage(model)

        verifyUiModel.value =
            VerifyUiModel.SignMessage(model = VerifySignMessageUiModel(model = model))

        return true
    }

    private fun getNormalizedCustomMessage(customMessage: CustomMessagePayload) =
        // For "eth_signTypedData_v4", the extension sends both the message and the domain
        // as pre-hashed values. Because these fields are already hashed, the original data
        // cannot be decoded from the resulting hex string.
        // Therefore, we display the raw hex instead.
        //
        // Reference:
        // https://github.com/ethers-io/ethers.js/blob/98c49d091eb84a9146dfba8476f18e4c3e3d1d31/src.ts/hash/typed-data.ts#L520
        // https://github.com/vultisig/vultisig-windows/blob/e7e5b388ca022c9e3f02a85346336b837857a856/core/inpage-provider/popup/view/resolvers/signMessage/overview/index.tsx#L36
        if (customMessage.method.equals(other = ETH_SIGN_TYPED_DATA_V4, ignoreCase = true)) {
            customMessage.message
        } else {
            customMessage.message.normalizeMessageFormat()
        }

    private suspend fun handleKeysignMessage(proto: KeysignMessageProto): Boolean {
        val message = mapKeysignMessageFromProto(proto)

        return !loadKeysignMessage(message.payload!!)
    }

    private suspend fun loadKeysignMessage(ksPayload: KeysignPayload): Boolean {
        if (
            !checkIsVaultCorrect(
                ksPayload.vaultPublicKeyECDSA,
                ksPayload.vaultLocalPartyID,
                ksPayload.libType,
            )
        ) {
            return false
        }

        if (deepLinkHelper.value?.hasResharePrefix() == true) {
            if (
                _currentVault.resharePrefix !=
                    requireNotNull(deepLinkHelper.value).getResharePrefix()
            ) {
                currentState.value = JoinKeysignState.Error(JoinKeysignError.WrongReShare)
                return false
            }
        }

        this@JoinKeysignViewModel._keysignPayload = ksPayload

        loadTransaction(ksPayload)
        return true
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

                val chain = srcToken.chain
                val (nativeTokenAddress, _) =
                    chainAccountAddressRepository.getAddress(nativeToken, _currentVault)
                val blockchainTransaction =
                    Swap(
                        coin = srcToken,
                        vault =
                            VaultData(
                                vaultHexPublicKey = _currentVault.getPubKeyByChain(chain),
                                vaultHexChainCode = _currentVault.hexChainCode,
                            ),
                        amount = swapPayload.srcTokenValue.value,
                        to = nativeTokenAddress,
                        callData = "",
                        approvalData = null,
                    )
                val calculatedFee =
                    withContext(Dispatchers.IO) {
                        feeServiceComposite.calculateFees(blockchainTransaction)
                    }
                val gasFee =
                    if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                        val utxoHelper = UtxoHelper.getHelper(_currentVault, srcToken.coinType)
                        val plan = utxoHelper.getBitcoinTransactionPlan(payload)
                        if (plan.error != SigningError.OK) {
                            Timber.e("UTXO plan error: ${plan.error.name}")
                        }
                        TokenValue(value = BigInteger.valueOf(plan.fee), token = nativeToken)
                    } else {
                        TokenValue(value = calculatedFee.amount, token = nativeToken)
                    }
                val estimatedNetworkGasFee: EstimatedGasFee =
                    gasFeeToEstimatedFee(
                        GasFeeParams(
                            gasLimit = BigInteger.valueOf(1),
                            gasFee = gasFee,
                            selectedToken = srcToken,
                        )
                    )
                val networkGasFeeFiatValue = estimatedNetworkGasFee.fiatValue

                val vaultName = _currentVault.name

                val resolvedProvider =
                    runCatching {
                            resolveProviderUseCase(
                                SwapSelectionContext(srcToken, dstToken, srcTokenValue)
                            )
                        }
                        .onFailure { Timber.w(it, "Failed to resolve swap provider in join flow") }
                        .getOrNull()
                val provider = resolvedProvider?.getSwapProviderId().orEmpty()

                when (swapPayload) {
                    is SwapPayload.EVM -> {
                        val oneInchSwapTxJson = swapPayload.data.quote.tx
                        val hasJupiterSwapProvider =
                            srcToken.chain == Chain.Solana && dstToken.chain == Chain.Solana
                        // LI.FI is the only aggregator that produces cross-chain EVM swaps, so
                        // treat src.chain != dst.chain as LI.FI even if provider resolution
                        // failed in this flow.
                        val isLiFi =
                            resolvedProvider == SwapProvider.LIFI ||
                                srcToken.chain != dstToken.chain

                        val feeToken =
                            when {
                                // Mirror iOS / SwapFormViewModel: LI.FI integrator fee is a
                                // percentage of the destination amount, denominated in the
                                // destination token. The raw "LIFI Fixed Fee" amount has no
                                // chainId and cannot be safely interpreted as source-native wei
                                // for cross-chain swaps (#3300).
                                isLiFi -> dstToken
                                hasJupiterSwapProvider -> srcToken
                                else -> nativeToken
                            }

                        val value =
                            when {
                                // VULT tier discount isn't available in the join flow, so this
                                // uses the base integrator rate. The difference vs. the initiator
                                // display is at most 0.5% of dstAmount.
                                isLiFi ->
                                    LiFiChainApi.integratorFeeAmount(
                                        dstAmount = dstTokenValue.value
                                    )
                                oneInchSwapTxJson.swapFee.isNotEmpty() &&
                                    oneInchSwapTxJson.swapFee.toBigIntegerOrNull() != null ->
                                    oneInchSwapTxJson.swapFee.toBigInteger()
                                else ->
                                    (oneInchSwapTxJson.gasPrice.toBigIntegerOrNull()
                                        ?: BigInteger.ZERO) *
                                        (oneInchSwapTxJson.gas.takeIf { it != 0L }
                                                ?: EvmHelper.DEFAULT_ETH_SWAP_GAS_UNIT)
                                            .toBigInteger()
                            }

                        val estimatedTokenFees = TokenValue(value = value, token = feeToken)

                        val estimatedFee =
                            convertTokenValueToFiat(feeToken, estimatedTokenFees, currency)

                        val swapTransaction =
                            SwapTransactionUiModel(
                                src =
                                    ValuedToken(
                                        value = mapTokenValueToDecimalUiString(srcTokenValue),
                                        token = srcToken,
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                convertTokenValueToFiat(
                                                    srcToken,
                                                    srcTokenValue,
                                                    currency,
                                                )
                                            ),
                                    ),
                                dst =
                                    ValuedToken(
                                        value = mapTokenValueToDecimalUiString(dstTokenValue),
                                        token = dstToken,
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                convertTokenValueToFiat(
                                                    dstToken,
                                                    dstTokenValue,
                                                    currency,
                                                )
                                            ),
                                    ),
                                providerFee =
                                    ValuedToken(
                                        token = feeToken,
                                        value = value.toString(),
                                        fiatValue = fiatValueToStringMapper(estimatedFee),
                                    ),
                                networkFee =
                                    ValuedToken(
                                        token = srcToken,
                                        value =
                                            mapTokenValueToDecimalUiString(
                                                estimatedNetworkGasFee.tokenValue
                                            ),
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                estimatedNetworkGasFee.fiatValue
                                            ),
                                    ),
                                networkFeeFormatted =
                                    mapTokenValueToDecimalUiString(
                                        estimatedNetworkGasFee.tokenValue
                                    ) + " ${estimatedNetworkGasFee.tokenValue.unit}",
                                totalFee =
                                    fiatValueToStringMapper(estimatedFee + networkGasFeeFiatValue),
                                provider = provider,
                            )

                        transactionTypeUiModel = TransactionTypeUiModel.Swap(swapTransaction)

                        transactionHistoryData = mapSwapTransactionToHistoryData(swapTransaction)
                        verifyUiModel.value =
                            VerifyUiModel.Swap(
                                VerifySwapUiModel(tx = swapTransaction, vaultName = vaultName)
                            )
                    }

                    is SwapPayload.ThorChain -> {
                        val quote =
                            swapQuoteRepository.getSwapQuote(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                dstAddress = swapPayload.data.toAddress,
                                tokenValue = srcTokenValue,
                            )

                        val estimatedFee = convertTokenValueToFiat(dstToken, quote.fees, currency)
                        val swapTransactionUiModel =
                            SwapTransactionUiModel(
                                src =
                                    ValuedToken(
                                        value = mapTokenValueToDecimalUiString(srcTokenValue),
                                        token = srcToken,
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                convertTokenValueToFiat(
                                                    srcToken,
                                                    srcTokenValue,
                                                    currency,
                                                )
                                            ),
                                    ),
                                dst =
                                    ValuedToken(
                                        value = mapTokenValueToDecimalUiString(dstTokenValue),
                                        token = dstToken,
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                convertTokenValueToFiat(
                                                    dstToken,
                                                    dstTokenValue,
                                                    currency,
                                                )
                                            ),
                                    ),
                                networkFee =
                                    ValuedToken(
                                        token = srcToken,
                                        value =
                                            mapTokenValueToDecimalUiString(
                                                estimatedNetworkGasFee.tokenValue
                                            ),
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                estimatedNetworkGasFee.fiatValue
                                            ),
                                    ),
                                providerFee =
                                    ValuedToken(
                                        token = dstToken,
                                        value = quote.fees.value.toString(),
                                        fiatValue = fiatValueToStringMapper(estimatedFee),
                                    ),
                                networkFeeFormatted =
                                    mapTokenValueToDecimalUiString(
                                        estimatedNetworkGasFee.tokenValue
                                    ) + " ${estimatedNetworkGasFee.tokenValue.unit}",
                                totalFee =
                                    fiatValueToStringMapper(estimatedFee + networkGasFeeFiatValue),
                                provider = provider,
                            )
                        transactionTypeUiModel = TransactionTypeUiModel.Swap(swapTransactionUiModel)
                        transactionHistoryData =
                            mapSwapTransactionToHistoryData(swapTransactionUiModel)
                        verifyUiModel.value =
                            VerifyUiModel.Swap(
                                VerifySwapUiModel(
                                    tx = swapTransactionUiModel,
                                    vaultName = vaultName,
                                )
                            )
                    }

                    is SwapPayload.MayaChain -> {
                        val isAffiliate = true

                        val quote =
                            swapQuoteRepository.getMayaSwapQuote(
                                srcToken = srcToken,
                                dstToken = dstToken,
                                dstAddress = swapPayload.data.toAddress,
                                tokenValue = srcTokenValue,
                                isAffiliate = isAffiliate,
                            )

                        val estimatedFee = convertTokenValueToFiat(dstToken, quote.fees, currency)
                        val swapTransactionUiModel =
                            SwapTransactionUiModel(
                                src =
                                    ValuedToken(
                                        value = mapTokenValueToDecimalUiString(srcTokenValue),
                                        token = srcToken,
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                convertTokenValueToFiat(
                                                    srcToken,
                                                    srcTokenValue,
                                                    currency,
                                                )
                                            ),
                                    ),
                                dst =
                                    ValuedToken(
                                        value = mapTokenValueToDecimalUiString(dstTokenValue),
                                        token = dstToken,
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                convertTokenValueToFiat(
                                                    dstToken,
                                                    dstTokenValue,
                                                    currency,
                                                )
                                            ),
                                    ),
                                providerFee =
                                    ValuedToken(
                                        token = dstToken,
                                        value = quote.fees.value.toString(),
                                        fiatValue = fiatValueToStringMapper(estimatedFee),
                                    ),
                                networkFee =
                                    ValuedToken(
                                        token = srcToken,
                                        value =
                                            mapTokenValueToDecimalUiString(
                                                estimatedNetworkGasFee.tokenValue
                                            ),
                                        fiatValue =
                                            fiatValueToStringMapper(
                                                estimatedNetworkGasFee.fiatValue
                                            ),
                                    ),
                                networkFeeFormatted =
                                    mapTokenValueToDecimalUiString(
                                        estimatedNetworkGasFee.tokenValue
                                    ) + " ${estimatedNetworkGasFee.tokenValue.unit}",
                                totalFee =
                                    fiatValueToStringMapper(estimatedFee + networkGasFeeFiatValue),
                                provider = provider,
                            )
                        transactionTypeUiModel = TransactionTypeUiModel.Swap(swapTransactionUiModel)
                        transactionHistoryData =
                            mapSwapTransactionToHistoryData(swapTransactionUiModel)
                        verifyUiModel.value =
                            VerifyUiModel.Swap(
                                VerifySwapUiModel(
                                    tx = swapTransactionUiModel,
                                    vaultName = vaultName,
                                )
                            )
                    }
                }
            }

            else -> {
                val isDeposit =
                    when (val specific = payload.blockChainSpecific) {
                        is BlockChainSpecific.MayaChain -> specific.isDeposit
                        is BlockChainSpecific.THORChain -> specific.isDeposit
                        else -> {
                            val memoUpper = payload.memo?.uppercase()
                            payload.coin.isSecuredAssetEligible() &&
                                (memoUpper?.contains("SECURE+:") == true)
                        }
                    }

                if (isDeposit) {
                    when (payload.blockChainSpecific) {
                        is BlockChainSpecific.MayaChain,
                        is BlockChainSpecific.THORChain,
                        is BlockChainSpecific.Ethereum,
                        is BlockChainSpecific.UTXO -> Unit

                        else ->
                            error(
                                "BlockChainSpecific ${payload.blockChainSpecific} is not supported"
                            )
                    }

                    val payloadToken = payload.coin
                    val chain = payloadToken.chain

                    val tokenValue =
                        TokenValue(
                            value = payload.toAmount,
                            unit = payloadToken.ticker,
                            decimals = payloadToken.decimal,
                        )

                    val vault =
                        withContext(Dispatchers.IO) { vaultRepository.get(vaultId) }
                            ?: error("Vault not found")

                    val blockchainTransaction =
                        Transfer(
                            coin = payloadToken,
                            vault =
                                VaultData(
                                    vaultHexChainCode = vault.hexChainCode,
                                    vaultHexPublicKey = vault.getPubKeyByChain(chain),
                                ),
                            amount = tokenValue.value,
                            to = payload.toAddress,
                            memo = payload.memo,
                            isMax = false,
                        )

                    val fees =
                        withContext(Dispatchers.IO) {
                            feeServiceComposite.calculateFees(blockchainTransaction)
                        }
                    val nativeCoin =
                        withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
                    val estimatedTokenFees = TokenValue(value = fees.amount, token = nativeCoin)

                    val totalGasAndFee =
                        gasFeeToEstimatedFee(
                            GasFeeParams(
                                gasLimit = BigInteger.valueOf(1),
                                gasFee = estimatedTokenFees,
                                selectedToken = payload.coin,
                            )
                        )

                    val depositTransactionUiModel =
                        DepositTransactionUiModel(
                            token =
                                ValuedToken(
                                    token = payload.coin,
                                    value = mapTokenValueToDecimalUiString(tokenValue),
                                    fiatValue = "",
                                ),
                            srcAddress = payload.coin.address,
                            dstAddress = payload.toAddress,
                            networkFeeTokenValue = totalGasAndFee.formattedTokenValue,
                            networkFeeFiatValue = totalGasAndFee.formattedFiatValue,
                            memo = payload.memo ?: "",
                        )
                    transactionTypeUiModel =
                        TransactionTypeUiModel.Deposit(depositTransactionUiModel)
                    transactionHistoryData =
                        mapDepositTransactionHistoryData(depositTransactionUiModel)
                    verifyUiModel.value =
                        VerifyUiModel.Deposit(VerifyDepositUiModel(depositTransactionUiModel))
                } else {
                    val payloadToken = payload.coin
                    val address = payloadToken.address
                    val chain = payloadToken.chain

                    val tokenValue =
                        TokenValue(
                            value = payload.toAmount,
                            unit = payloadToken.ticker,
                            decimals = payloadToken.decimal,
                        )

                    val vault =
                        withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: return

                    val blockchainTransaction =
                        Transfer(
                            coin = payloadToken,
                            vault =
                                VaultData(
                                    vaultHexChainCode = vault.hexChainCode,
                                    vaultHexPublicKey = vault.getPubKeyByChain(chain),
                                ),
                            amount = tokenValue.value,
                            to = payload.toAddress,
                            memo = payload.memo,
                            isMax = false,
                        )

                    val fees =
                        withContext(Dispatchers.IO) {
                            feeServiceComposite.calculateFees(blockchainTransaction)
                        }
                    val nativeCoin =
                        withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
                    val gasFee =
                        if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                            val utxoHelper = UtxoHelper.getHelper(vault, payloadToken.coinType)
                            val plan = utxoHelper.getBitcoinTransactionPlan(payload)
                            if (plan.error != SigningError.OK) {
                                Timber.e("UTXO plan error: ${plan.error.name}")
                            }
                            TokenValue(value = BigInteger.valueOf(plan.fee), token = nativeCoin)
                        } else {
                            TokenValue(value = fees.amount, token = nativeCoin)
                        }

                    val totalGasAndFee =
                        gasFeeToEstimatedFee(
                            GasFeeParams(
                                gasLimit = BigInteger.valueOf(1),
                                gasFee = gasFee,
                                selectedToken = payload.coin,
                            )
                        )
                    val functionInfo = getTransactionFunctionInfo(payload.memo, chain)
                    val normalizedSignAminoJson =
                        kotlinx.serialization.json.buildJsonArray {
                            payload.signAmino?.msgs?.forEach { cosmosMsg ->
                                cosmosMsg?.type ?: return@forEach
                                addJsonObject {
                                    val type = cosmosMsg.type
                                    val valueElem =
                                        try {
                                            json.parseToJsonElement(cosmosMsg.value)
                                        } catch (e: Exception) {
                                            kotlinx.serialization.json.JsonPrimitive(
                                                cosmosMsg.value
                                            )
                                        }

                                    put("type", kotlinx.serialization.json.JsonPrimitive(type))
                                    put("value", valueElem)
                                }
                            }
                        }

                    val normalizedSignAmino =
                        json.encodeToString(normalizedSignAminoJson).takeIf {
                            !normalizedSignAminoJson.isEmpty()
                        } ?: ""
                    val signDirect =
                        payload.signDirect?.let { json.encodeToString(parseCosmosMessage(it)) }
                            ?: ""

                    val signSolana = payload.signSolana?.rawTransactions?.firstOrNull() ?: ""
                    val transaction =
                        Transaction(
                            id = UUID.randomUUID().toString(),
                            vaultId = payload.vaultPublicKeyECDSA,
                            chainId = chain.id,
                            token = payloadToken,
                            srcAddress = address,
                            dstAddress = payload.toAddress,
                            tokenValue = tokenValue,
                            fiatValue = convertTokenValueToFiat(payloadToken, tokenValue, currency),
                            gasFee = gasFee,
                            memo = payload.memo.takeIf { functionInfo == null },
                            estimatedFee = totalGasAndFee.formattedFiatValue,
                            blockChainSpecific = payload.blockChainSpecific,
                            totalGas = totalGasAndFee.formattedTokenValue,
                            signAmino = normalizedSignAmino,
                            signDirect = signDirect,
                            signSolana = signSolana,
                        )

                    val transactionToUiModel = mapTransactionToUiModel(transaction)

                    val allVaults = withContext(Dispatchers.IO) { vaultRepository.getAll() }
                    val normalizedSrcAddress = normalizeAddressForLookup(address)
                    val srcVaultName =
                        allVaults
                            .firstOrNull { v ->
                                v.coins.any {
                                    it.chain == chain &&
                                        normalizeAddressForLookup(it.address) ==
                                            normalizedSrcAddress
                                }
                            }
                            ?.name
                    val normalizedDstAddress = normalizeAddressForLookup(payload.toAddress)
                    val dstVaultName =
                        allVaults
                            .firstOrNull { v ->
                                v.coins.any {
                                    it.chain == chain &&
                                        normalizeAddressForLookup(it.address) ==
                                            normalizedDstAddress
                                }
                            }
                            ?.name
                    val isSavedInAddressBook =
                        dstVaultName == null &&
                            addressBookRepository.entryExists(chain.id, payload.toAddress)
                    val dstAddressBookTitle =
                        if (isSavedInAddressBook) {
                            runCatching {
                                    addressBookRepository
                                        .getEntry(chain.id, payload.toAddress)
                                        .title
                                }
                                .getOrNull()
                        } else null

                    val namedTransactionUiModel =
                        transactionToUiModel.copy(
                            srcVaultName = srcVaultName,
                            dstVaultName = dstVaultName,
                            dstAddressBookTitle = dstAddressBookTitle,
                        )
                    transactionTypeUiModel = TransactionTypeUiModel.Send(namedTransactionUiModel)
                    transactionHistoryData = mapTransactionHistoryData(namedTransactionUiModel)
                    verifyUiModel.value =
                        VerifyUiModel.Send(
                            VerifyTransactionUiModel(
                                transaction = namedTransactionUiModel,
                                functionSignature = functionInfo?.signature,
                                functionInputs = functionInfo?.inputs,
                            )
                        )
                    val uiModel = verifyUiModel.value
                    if (uiModel is VerifyUiModel.Send) {
                        scanTransaction(transaction)
                    }
                }
            }
        }
    }

    private fun scanTransaction(transaction: Transaction) {
        viewModelScope.launch {
            val chain = transaction.token.chain
            val isChainSupported =
                securityScannerService.getSupportedChainsByFeature().isChainSupported(chain) &&
                    securityScannerService.isSecurityServiceEnabled()

            if (!isChainSupported) {
                return@launch
            }

            // update loading status
            updateSendUiModel(verifyUiModel) { currentModel ->
                currentModel.copy(txScanStatus = TransactionScanStatus.Scanning)
            }

            try {
                // run scanner and update UI widget
                val securityScannerTransaction =
                    securityScannerService.createSecurityScannerTransaction(transaction)
                val scanResult =
                    withContext(Dispatchers.IO) {
                        securityScannerService.scanTransaction(securityScannerTransaction)
                    }
                updateSendUiModel(verifyUiModel) { currentModel ->
                    currentModel.copy(txScanStatus = TransactionScanStatus.Scanned(scanResult))
                }
            } catch (e: Exception) {
                updateSendUiModel(verifyUiModel) { currentModel ->
                    currentModel.copy(
                        txScanStatus =
                            TransactionScanStatus.Error(
                                e.message ?: "Security Scanner Failed",
                                BLOCKAID_PROVIDER,
                            )
                    )
                }
            }
        }
    }

    private fun updateSendUiModel(
        flow: MutableStateFlow<VerifyUiModel>,
        updateBlock: (VerifyTransactionUiModel) -> VerifyTransactionUiModel,
    ) {
        flow.update { currentVerifyModel ->
            if (currentVerifyModel is VerifyUiModel.Send) {
                val updatedSendModel = updateBlock(currentVerifyModel.model)
                VerifyUiModel.Send(updatedSendModel)
            } else {
                // If it's not a Send model, return the current state unchanged.
                // `update` requires you to return a new state
                // for every call, even if no change is desired.
                currentVerifyModel
            }
        }
    }

    private suspend fun fetchAndHandleCustomMessagePayload(serverAddress: String): Boolean {
        val payload = routerApi.getPayload(serverAddress, customPayloadId)
        if (payload.isEmpty()) return false
        val rawPayload = decompressQr(payload.decodeBase64Bytes())
        val fetchedPayload = protoBuf.decodeFromByteArray<CustomMessagePayload>(rawPayload)
        val vaultPublicKeyEcdsa =
            fetchedPayload.vaultPublicKeyEcdsa.ifEmpty {
                deepLinkHelper.value?.getParameter(VAULT_PARAMETER) ?: ""
            }
        return handleCustomMessage(fetchedPayload.copy(vaultPublicKeyEcdsa = vaultPublicKeyEcdsa))
    }

    private fun onServerAddressDiscovered(address: String) {
        _serverAddress = address
        if (!payloadId.isEmpty() && tempKeysignMessageProto != null) {
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to fetch keysign payload")
                    currentState.value =
                        JoinKeysignState.Error(JoinKeysignError.FailedConnectToServer)
                }
            ) {
                // when Payload is not in the QRCode
                routerApi.getPayload(_serverAddress, payloadId).let { payload ->
                    if (payload.isNotEmpty()) {
                        val rawPayload = decompressQr(payload.decodeBase64Bytes())
                        val payloadProto =
                            protoBuf.decodeFromByteArray<KeysignPayloadProto>(rawPayload)
                        val keysignMsgProto =
                            KeysignMessageProto(
                                keysignPayload = payloadProto,
                                sessionId = tempKeysignMessageProto!!.sessionId,
                                serviceName = tempKeysignMessageProto!!.serviceName,
                                encryptionKeyHex = tempKeysignMessageProto!!.encryptionKeyHex,
                                useVultisigRelay = _useVultisigRelay,
                                payloadId = payloadId,
                            )
                        if (handleKeysignMessage(keysignMsgProto)) {
                            return@safeLaunch
                        }
                        currentState.value = JoinKeysignState.JoinKeysign
                    }
                }
            }
        } else if (customPayloadId.isNotEmpty() && tempKeysignMessageProto != null) {
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to fetch custom message payload")
                    currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
                }
            ) {
                if (fetchAndHandleCustomMessagePayload(_serverAddress)) {
                    currentState.value = JoinKeysignState.JoinKeysign
                } else {
                    currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
                }
            }
        } else {
            currentState.value = JoinKeysignState.JoinKeysign
        }

        // discovery finished
        _discoveryListener?.let { _nsdManager?.stopServiceDiscovery(it) }
    }

    fun discoveryMediator(nsdManager: NsdManager) {
        _discoveryListener =
            MediatorServiceDiscoveryListener(nsdManager, _serviceName, ::onServerAddressDiscovered)
        _nsdManager = nsdManager
        nsdManager.discoverServices("_http._tcp.", NsdManager.PROTOCOL_DNS_SD, _discoveryListener)
    }

    fun joinKeysign() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                try {
                    Timber.tag("JoinKeysignViewModel").d("Joining keysign")
                    sessionApi.startSession(_serverAddress, _sessionID, listOf(_localPartyID))
                    waitForKeysignToStart()
                    currentState.value = JoinKeysignState.WaitingForKeysignStart
                } catch (e: HttpException) {
                    Timber.tag("JoinKeysignViewModel")
                        .e(
                            "Failed to join keysign (HTTP ${e.statusCode}): %s",
                            e.stackTraceToString(),
                        )
                    currentState.value =
                        if (e.statusCode >= 500) {
                            JoinKeysignState.Error(JoinKeysignError.RelayUnavailable)
                        } else {
                            JoinKeysignState.Error(
                                JoinKeysignError.FailedToStart(e.message.toString())
                            )
                        }
                } catch (e: Exception) {
                    Timber.tag("JoinKeysignViewModel")
                        .e("Failed to join keysign: %s", e.stackTraceToString())
                    currentState.value =
                        JoinKeysignState.Error(JoinKeysignError.FailedToStart(e.message.toString()))
                }
            }
        }
    }

    fun tryAgain() {
        viewModelScope.launch {
            val keysignError = currentState.value as JoinKeysignState.Error
            when (keysignError.errorType) {
                JoinKeysignError.MissingRequiredVault,
                JoinKeysignError.WrongVault,
                JoinKeysignError.WrongVaultShare ->
                    navigator.route(
                        Route.Home(showVaultList = true),
                        opts = NavigationOptions(clearBackStack = true),
                    )

                JoinKeysignError.RelayUnavailable -> {
                    currentState.value = JoinKeysignState.JoinKeysign
                    joinKeysign()
                }

                else -> navigator.navigate(Destination.Back)
            }
        }
    }

    private fun cleanUp() {
        _jobWaitingForKeysignStart?.cancel()
    }

    private fun waitForKeysignToStart() {
        _jobWaitingForKeysignStart =
            viewModelScope.launch {
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

    private suspend fun checkKeygenStarted(): Boolean {
        try {
            this._keysignCommittee = sessionApi.checkCommittee(_serverAddress, _sessionID)
            Timber.d("Keysign committee: $_keysignCommittee")
            Timber.d("local party: $_localPartyID")
            if (this._keysignCommittee.contains(_localPartyID)) {
                when {
                    _keysignPayload != null -> {
                        val payload = _keysignPayload ?: error("keysignPayload unexpectedly null")
                        messagesToSign =
                            SigningHelper.getKeysignMessages(
                                payload = payload,
                                vault = _currentVault,
                            )
                    }

                    customMessagePayload != null -> {
                        val payload =
                            customMessagePayload ?: error("customMessagePayload unexpectedly null")
                        messagesToSign = SigningHelper.getKeysignMessages(payload)
                    }

                    else -> {
                        Timber.e("Both keysign payload and custom message payload are null")
                        currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
                        return false
                    }
                }

                return true
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to check keysign start")
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
                navigator.route(Route.Home(), NavigationOptions(clearBackStack = true))
            } else {
                navigator.navigate(Destination.Back)
            }
        }
    }

    override fun onCleared() {
        cleanUp()
        super.onCleared()
    }

    private suspend fun getTransactionFunctionInfo(memo: String?, chain: Chain): FunctionInfo? {
        if (chain.standard != TokenStandard.EVM || memo.isNullOrEmpty()) return null

        val functionSignature = fourByteRepository.decodeFunction(memo)
        val functionInputs =
            if (functionSignature != null) {
                fourByteRepository.decodeFunctionArgs(functionSignature, memo) ?: return null
            } else return null
        return FunctionInfo(functionSignature, functionInputs)
    }
}
