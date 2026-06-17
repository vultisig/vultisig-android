@file:OptIn(ExperimentalSerializationApi::class, ExperimentalStdlibApi::class)

package com.vultisig.wallet.ui.models.keysign

import android.net.nsd.NsdManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.RouterApi
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.ZcashApi
import com.vultisig.wallet.data.api.chains.ton.TonApi
import com.vultisig.wallet.data.api.errors.SwapException
import com.vultisig.wallet.data.api.utils.HttpException
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.common.DeepLinkHelper
import com.vultisig.wallet.data.common.Endpoints
import com.vultisig.wallet.data.common.normalizeMessageFormat
import com.vultisig.wallet.data.mappers.KeysignMessageFromProtoMapper
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.TssKeysignType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.isSecuredAssetEligible
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.proto.v1.KeysignMessageProto
import com.vultisig.wallet.data.models.proto.v1.KeysignPayloadProto
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.securityscanner.blockaid.BlockaidSimulationService
import com.vultisig.wallet.data.securityscanner.isChainSupported
import com.vultisig.wallet.data.usecases.DecompressQrUseCase
import com.vultisig.wallet.data.usecases.ParseCosmosMessageUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.hero.HeroContent
import com.vultisig.wallet.ui.models.TransactionScanStatus
import com.vultisig.wallet.ui.models.VerifyTransactionUiModel
import com.vultisig.wallet.ui.models.deposit.VerifyDepositUiModel
import com.vultisig.wallet.ui.models.keygen.MediatorServiceDiscoveryListener
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.models.sign.VerifySignMessageUiModel
import com.vultisig.wallet.ui.models.swap.VerifySwapUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.usecases.BuildHeroContentUseCase
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import io.ktor.util.decodeBase64Bytes
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Locale
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import timber.log.Timber
import vultisig.keysign.v1.CustomMessagePayload
import vultisig.keysign.v1.KeysignMessage
import vultisig.keysign.v1.TransactionType
import wallet.core.jni.TONAddressConverter

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

    /**
     * The initiator never started the signing ceremony within the timeout — it likely abandoned the
     * keysign (closed the app or lost connectivity). Retryable.
     */
    data object Timeout : JoinKeysignError(R.string.join_keysign_start_timeout.asUiText())

    /**
     * This vault is missing the Bitcoin or QBTC account the claim hash is derived from, so it
     * cannot co-sign the QBTC claim.
     */
    data object MissingQbtcClaimAccount :
        JoinKeysignError(R.string.join_keysign_qbtc_claim_missing_account.asUiText())
}

/** Raised when the messages to sign cannot be prepared once the ceremony starts. */
internal class KeysignMessagesException(message: String) : Exception(message)

/** Raised when polling the relay for committee membership fails (network/relay error). */
internal class KeysignCheckException(message: String) : Exception(message)

/** How [awaitKeysignStart] finished polling for the initiator to start the ceremony. */
internal sealed interface KeysignStartOutcome {
    /** The local party is in the committee — the ceremony has begun. */
    data object Started : KeysignStartOutcome

    /** Preparing the messages to sign failed; carries the reason for the error state. */
    data class FailedToPrepare(val message: String) : KeysignStartOutcome

    /** Polling the relay for the committee failed; carries the reason for the error state. */
    data class FailedToCheck(val message: String) : KeysignStartOutcome

    /** The deadline elapsed without the ceremony starting (initiator likely abandoned). */
    data object TimedOut : KeysignStartOutcome
}

/**
 * Polls [checkStarted] every [pollInterval] until it reports the ceremony has started, bounded by
 * [timeout]. Without the bound a joiner spins forever when the initiator abandons the keysign
 * (issue #4856). Only a plain `false` ("not started yet") keeps the loop running. A
 * [KeysignMessagesException] ends the wait with [KeysignStartOutcome.FailedToPrepare] and a
 * [KeysignCheckException] with [KeysignStartOutcome.FailedToCheck], so a real poll failure surfaces
 * immediately instead of being masked by the timeout; exceeding [timeout] yields
 * [KeysignStartOutcome.TimedOut].
 */
internal suspend fun awaitKeysignStart(
    timeout: Duration,
    pollInterval: Duration,
    checkStarted: suspend () -> Boolean,
): KeysignStartOutcome =
    withTimeoutOrNull(timeout) {
        while (true) {
            try {
                if (checkStarted()) {
                    return@withTimeoutOrNull KeysignStartOutcome.Started
                }
            } catch (e: KeysignMessagesException) {
                return@withTimeoutOrNull KeysignStartOutcome.FailedToPrepare(
                    e.message ?: "Failed to prepare messages to sign"
                )
            } catch (e: KeysignCheckException) {
                return@withTimeoutOrNull KeysignStartOutcome.FailedToCheck(
                    e.message ?: "Failed to check keysign start"
                )
            }
            delay(pollInterval)
        }
        error("unreachable") // the loop only exits via return@withTimeoutOrNull
    } ?: KeysignStartOutcome.TimedOut

/** Bounds how long a joiner waits for the initiator to start the ceremony before failing. */
private val WAIT_FOR_KEYSIGN_START_TIMEOUT = 2.minutes
private val KEYSIGN_START_POLL_INTERVAL = 1.seconds

sealed interface JoinKeysignState {
    data object DiscoveringSessionID : JoinKeysignState

    data object DiscoverService : JoinKeysignState

    data object JoinKeysign : JoinKeysignState

    data object WaitingForKeysignStart : JoinKeysignState

    data object Keysign : JoinKeysignState

    /**
     * QBTC claim co-sign: [txHash] null while signing, set once the initiator pushes the result.
     */
    data class QbtcClaim(val txHash: String?, val totalSats: Long?) : JoinKeysignState

    data class Error(val errorType: JoinKeysignError) : JoinKeysignState
}

internal sealed class VerifyUiModel {

    data class Send(val model: VerifyTransactionUiModel) : VerifyUiModel()

    data class Swap(val model: VerifySwapUiModel) : VerifyUiModel()

    data class Deposit(val model: VerifyDepositUiModel) : VerifyUiModel()

    data class SignMessage(val model: VerifySignMessageUiModel) : VerifyUiModel()
}

internal data class FunctionInfo(
    val signature: String,
    val inputs: String?,
    val functionName: String? = null,
)

@HiltViewModel
internal class JoinKeysignViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val appCurrencyRepository: AppCurrencyRepository,
    private val vaultRepository: VaultRepository,
    private val mapKeysignMessageFromProto: KeysignMessageFromProtoMapper,
    private val protoBuf: ProtoBuf,
    private val decompressQr: DecompressQrUseCase,
    private val sessionApi: SessionApi,
    private val zcashApi: ZcashApi,
    private val routerApi: RouterApi,
    private val tonApi: TonApi,
    private val securityScannerService: SecurityScannerContract,
    private val keysignViewModelFactory: KeysignViewModel.Factory,
    private val blockaidSimulationService: BlockaidSimulationService,
    private val buildHeroContent: BuildHeroContentUseCase,
    private val qbtcClaimCosign: QbtcClaimCosignUseCase,
    private val joinSwapUiModelBuilder: JoinSwapUiModelBuilder,
    private val joinDepositUiModelBuilder: JoinDepositUiModelBuilder,
    private val joinSendUiModelBuilder: JoinSendUiModelBuilder,
    private val parseCosmosMessage: ParseCosmosMessageUseCase,
) : ViewModel() {
    companion object {
        private const val VAULT_PARAMETER = "vault"

        private const val ETH_SIGN_TYPED_DATA_V4 = "eth_signTypedData_v4"
    }

    private val args = savedStateHandle.toRoute<Route.Keysign.Join>()
    private val vaultId: String = args.vaultId
    private val qrBase64: String = args.qr
    private var _currentVault: Vault = Vault(id = UUID.randomUUID().toString(), "temp vault")
    private val _currentState =
        MutableStateFlow<JoinKeysignState>(JoinKeysignState.DiscoveringSessionID)
    /** Read-only view of the join-keysign flow state the screen observes. */
    val currentState: StateFlow<JoinKeysignState> = _currentState.asStateFlow()
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
        set(value) {
            field = value
            // Mirror dappMetadata into the StateFlow so the verify banner is observable on its own,
            // independent of [verifyUiModel] emission ordering.
            _dappMetadata.value = value?.dappMetadata
        }

    private val _dappMetadata = MutableStateFlow<DAppMetadata?>(null)
    private var customMessagePayload: CustomMessagePayload? = null
    private var messagesToSign: List<String> = emptyList()

    private var _jobWaitingForKeysignStart: Job? = null
    private var blockaidSimulationJob: Job? = null
    private var tonJettonHeroJob: Job? = null
    private val isJoiningKeysign = AtomicBoolean(false)
    private var isNavigateToHome: Boolean = false

    private var transactionTypeUiModel: TransactionTypeUiModel? = null
    private var transactionHistoryData: TransactionHistoryData? = null
    private var payloadId: String = ""
    private var customPayloadId: String = ""
    private var tempKeysignMessageProto: KeysignMessageProto? = null

    private val deepLinkHelper = MutableStateFlow<DeepLinkHelper?>(null)

    /**
     * dApp identity attached to the keysign request, if any. Read by the verify and done banners on
     * the joining-device path. Independent of [verifyUiModel] so every variant (Send/Swap/Deposit)
     * shares one source.
     *
     * Driven by the custom setter on [_keysignPayload] so consumers observing this flow are
     * notified the moment the payload is parsed — no implicit dependency on the ordering of
     * `verifyUiModel.value = …` emissions.
     */
    val dappMetadata: StateFlow<DAppMetadata?> = _dappMetadata.asStateFlow()

    val keysignViewModel: KeysignViewModel
        get() =
            keysignViewModelFactory.create(
                vault = _currentVault,
                keysignCommittee = _keysignCommittee,
                serverUrl = _serverAddress,
                sessionId = _sessionID,
                encryptionKeyHex = _encryptionKeyHex,
                messagesToSign = messagesToSign,
                keyType =
                    _keysignPayload?.coin?.chain?.TssKeysignType
                        ?: customMessagePayload?.chain?.let { raw ->
                            runCatching { Chain.fromRaw(raw).TssKeysignType }.getOrNull()
                        }
                        ?: TssKeyType.ECDSA,
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
                            _currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
                            return@launch
                        }
                    }
                    if (_keysignPayload?.isQbtcClaim == true) {
                        startQbtcClaimCosign()
                    } else {
                        _currentState.value = JoinKeysignState.JoinKeysign
                    }
                } else {
                    _currentState.value = JoinKeysignState.DiscoverService
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SwapException.NetworkConnection) {
                Timber.d(e, "Network connection failure during QR scan")
                _currentState.value = JoinKeysignState.Error(JoinKeysignError.FailedConnectToServer)
            } catch (e: UnknownHostException) {
                Timber.d(e, "Failed to resolve request")
                _currentState.value = JoinKeysignState.Error(JoinKeysignError.FailedConnectToServer)
            } catch (e: SocketException) {
                Timber.d(e, "Socket failure during QR scan")
                _currentState.value = JoinKeysignState.Error(JoinKeysignError.FailedConnectToServer)
            } catch (e: SocketTimeoutException) {
                Timber.d(e, "Socket timeout during QR scan")
                _currentState.value = JoinKeysignState.Error(JoinKeysignError.FailedConnectToServer)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.d(e, "Failed to parse QR code")
                _currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
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
                _currentState.value = JoinKeysignState.Error(JoinKeysignError.MissingRequiredVault)
            return false
        }

        if (localPartyId == _localPartyID) {
            _currentState.value = JoinKeysignState.Error(JoinKeysignError.WrongVaultShare)
            return false
        }

        if (libType != null && libType != _currentVault.libType) {
            _currentState.value = JoinKeysignState.Error(JoinKeysignError.WrongLibType)
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
                _currentState.value = JoinKeysignState.Error(JoinKeysignError.WrongReShare)
                return false
            }
        }

        this@JoinKeysignViewModel._keysignPayload = ksPayload

        // A QBTC claim payload is a flag carrier with no real tx body — skip the Send/verify
        // UI build; startQbtcClaimCosign() drives the co-sign once the server address is set.
        if (ksPayload.isQbtcClaim) return true

        loadTransaction(ksPayload)
        return true
    }

    private suspend fun loadTransaction(payload: KeysignPayload) {
        val currency = appCurrencyRepository.currency.first()
        val swapPayload = payload.swapPayload
        when {
            swapPayload != null ->
                applyVerifyResult(
                    joinSwapUiModelBuilder.build(
                        payload = payload,
                        swapPayload = swapPayload,
                        vault = _currentVault,
                        currency = currency,
                    )
                )

            isDepositPayload(payload) ->
                applyVerifyResult(joinDepositUiModelBuilder.build(payload, vaultId))

            else -> {
                val sendResult =
                    joinSendUiModelBuilder.build(
                        payload = payload,
                        srcVaultName = _currentVault.name,
                        vaultId = vaultId,
                        currency = currency,
                    ) ?: return
                applyVerifyResult(sendResult.result)
                // Kick off the hero resolution in parallel with the existing security scan; the
                // hero refresh and the badge refresh happen independently so neither blocks the
                // other and the UI doesn't go through an "all loading at once" state. Blockaid
                // doesn't cover TON, so a TonConnect request resolves its jetton hero from the
                // decoded BOC instead.
                val chain = payload.coin.chain
                if (chain == Chain.Ton && payload.signTon != null) {
                    loadTonDappHero(payload, sendResult.vaultCoins)
                } else {
                    loadBlockaidSimulation(payload, sendResult.functionName)
                }
                scanTransaction(sendResult.transaction)
            }
        }
    }

    /**
     * Whether [payload] is a deposit (THORChain/MayaChain deposit, or a SECURE+ secured-asset
     * memo). PSBT co-signs are initiated by an external dApp and are never a deposit, so they exit
     * before the memo-keyword heuristic — a payload carrying `SECURE+:` in `payload.memo` (BTC is
     * `isSecuredAssetEligible`) must not be flagged `isDeposit=true`.
     */
    private fun isDepositPayload(payload: KeysignPayload): Boolean =
        when (val specific = payload.blockChainSpecific) {
            is BlockChainSpecific.MayaChain -> specific.isDeposit
            is BlockChainSpecific.THORChain -> specific.isDeposit
            is BlockChainSpecific.BitcoinPSBT -> false
            // A joining device has no DepositTransactionRepository entry, so it recovers the
            // initiator's Cosmos deposit classification from the transmitted payload: IBC transfers
            // and staking SignDocs (issue #4939). Plain MsgSend / dApp txs stay sends.
            is BlockChainSpecific.Cosmos ->
                specific.transactionType == TransactionType.TRANSACTION_TYPE_IBC_TRANSFER ||
                    payload.isCosmosStakingDeposit(parseCosmosMessage)
            else -> {
                val memoUpper = payload.memo?.uppercase(Locale.ROOT)
                payload.coin.isSecuredAssetEligible() && (memoUpper?.contains("SECURE+:") == true)
            }
        }

    /**
     * Atomically assigns the three outputs every per-type builder produces. Order matches the
     * inline branches it replaced: [transactionTypeUiModel] and [transactionHistoryData] are set
     * before [verifyUiModel] so the send branch's background enrichment (which mutates
     * [transactionTypeUiModel] and reads [verifyUiModel]) sees a consistent state.
     */
    private fun applyVerifyResult(result: JoinKeysignVerifyResult) {
        transactionTypeUiModel = result.transactionTypeUiModel
        transactionHistoryData = result.transactionHistoryData
        verifyUiModel.value = result.verifyUiModel
    }

    /**
     * Resolves the Blockaid hero for the current keysign payload and pushes the result into the
     * shared [verifyUiModel].
     *
     * Runs on a background dispatcher; the cache + inflight coalescing inside
     * [BlockaidSimulationService] makes calling this on every screen entry cheap. Failures degrade
     * silently to the title-only fallback — the service swallows errors and returns
     * [BlockaidKeysignScanResult.EMPTY].
     */
    private fun loadBlockaidSimulation(payload: KeysignPayload, decodedFunctionName: String?) {
        // Cancel any in-flight scan before starting a new one. NSD can surface the same mediator
        // service more than once (for example after a transient connectivity blip) and would
        // otherwise launch concurrent coroutines that race to update the same StateFlow.
        blockaidSimulationJob?.cancel()
        blockaidSimulationJob =
            viewModelScope.safeLaunch(
                onError = { Timber.w(it, "Blockaid simulation failed during dApp signing") }
            ) {
                val result = withContext(Dispatchers.IO) { blockaidSimulationService.scan(payload) }
                val hero =
                    buildHeroContent(
                        simulation = result.simulation,
                        decodedFunctionName = decodedFunctionName,
                        didLoadSimulation = true,
                    )
                updateSendUiModel(verifyUiModel) { current ->
                    current.copy(transaction = current.transaction.copy(heroContent = hero))
                }
                // Mirror the resolved hero into [transactionTypeUiModel] so the
                // done screen's `KeysignViewModel` carries the same content forward
                // — the cache covers the same lookup, but updating in place avoids
                // a per-screen re-fetch and a flash of "loading" state on done.
                (transactionTypeUiModel as? TransactionTypeUiModel.Send)?.let { send ->
                    transactionTypeUiModel =
                        TransactionTypeUiModel.Send(send.tx.copy(heroContent = hero))
                }
            }
    }

    /**
     * Resolve the jetton hero for a TonConnect request from the decoded message bodies. Surfaces
     * the first vault-held jetton transfer's real amount + ticker + logo in place of the misleading
     * gas value. Best-effort: on a network failure or an unrecognised jetton the verify screen
     * keeps its existing display. Mirrors [loadBlockaidSimulation] — Blockaid doesn't cover TON, so
     * this is the TON hero path. Cancels any prior run (NSD can re-fire) and pushes the hero into
     * both the verify model and [transactionTypeUiModel] so the done screen carries it forward.
     */
    private fun loadTonDappHero(payload: KeysignPayload, vaultCoins: List<Coin>) {
        val messages = payload.signTon?.tonMessages?.filterNotNull().orEmpty()
        if (messages.isEmpty()) return
        tonJettonHeroJob?.cancel()
        tonJettonHeroJob =
            viewModelScope.safeLaunch(
                onError = { Timber.w(it, "TON dApp hero resolution failed during dApp signing") }
            ) {
                val hero =
                    withContext(Dispatchers.IO) {
                        // Prefer the "You're swapping" hero for a gated DEX swap; otherwise fall
                        // back
                        // to the single-sided jetton-transfer hero. Both are best-effort.
                        resolveTonSwapHero(
                            messages = messages,
                            nativeTon =
                                TonHeroCoin(
                                    ticker = Coins.Ton.TON.ticker,
                                    decimals = Coins.Ton.TON.decimal,
                                    logo = Coins.Ton.TON.logo,
                                ),
                            toUserFriendly = {
                                TONAddressConverter.toUserFriendly(it, true, false)
                            },
                            resolveCoinByWallet = { wallet ->
                                resolveTonCoinByWallet(wallet, vaultCoins)
                            },
                            resolveDedustOutputCoin = { pool ->
                                resolveTonDedustOutputCoin(pool, vaultCoins)
                            },
                        )
                            ?: resolveTonJettonHero(messages, vaultCoins) { wallet ->
                                    tonApi.getJettonMasterAddress(wallet)
                                }
                                ?.let { HeroContent.Send(title = null, coin = it) }
                    } ?: return@safeLaunch
                pushTonHero(hero)
            }
    }

    /**
     * Resolve a jetton wallet to its display coin for a swap leg: vault-tracked tokens first
     * (richest metadata), then the on-chain jetton master. Returns `null` when the wallet maps to
     * no known token, so the swap hero degrades rather than mislabelling the asset.
     */
    private suspend fun resolveTonCoinByWallet(
        wallet: String,
        vaultCoins: List<Coin>,
    ): TonHeroCoin? {
        val master = tonApi.getJettonMasterAddress(wallet) ?: return null
        return resolveTonCoinByMaster(master, vaultCoins)
    }

    /**
     * Resolve a DeDust swap's output token. The swap addresses the liquidity **pool**, not the
     * output jetton wallet, so the output master is read from the pool's `get_assets`.
     */
    private suspend fun resolveTonDedustOutputCoin(
        poolAddress: String,
        vaultCoins: List<Coin>,
    ): TonHeroCoin? {
        val master = tonApi.getDedustPoolOutputMaster(poolAddress) ?: return null
        return resolveTonCoinByMaster(master, vaultCoins)
    }

    /**
     * Resolve a jetton master to its display coin: vault-tracked tokens first (richest metadata),
     * then the built-in [Coins] registry, then on-chain metadata. Returns `null` when nothing
     * resolves, so the swap hero degrades rather than mislabelling the asset. [masterAddress] may
     * be raw or user-friendly; it is canonicalized for comparison against the friendly-form
     * contract addresses the registry/vault store.
     */
    private suspend fun resolveTonCoinByMaster(
        masterAddress: String,
        vaultCoins: List<Coin>,
    ): TonHeroCoin? {
        val master = TONAddressConverter.toUserFriendly(masterAddress, true, false) ?: masterAddress
        (vaultCoins.asSequence() + Coins.coins[Chain.Ton].orEmpty().asSequence())
            .firstOrNull {
                it.chain == Chain.Ton && !it.isNativeToken && it.contractAddress == master
            }
            ?.let {
                return TonHeroCoin(ticker = it.ticker, decimals = it.decimal, logo = it.logo)
            }
        return tonApi.getJettonMetadata(master)?.let {
            TonHeroCoin(ticker = it.ticker, decimals = it.decimals, logo = it.logo ?: "")
        }
    }

    /**
     * Push a resolved TON dApp hero into the verify model and mirror it into
     * [transactionTypeUiModel] so the done screen's `KeysignViewModel` carries the same content
     * forward.
     */
    private fun pushTonHero(hero: HeroContent) {
        updateSendUiModel(verifyUiModel) { current ->
            current.copy(transaction = current.transaction.copy(heroContent = hero))
        }
        (transactionTypeUiModel as? TransactionTypeUiModel.Send)?.let { send ->
            transactionTypeUiModel = TransactionTypeUiModel.Send(send.tx.copy(heroContent = hero))
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
                if (e is kotlinx.coroutines.CancellationException) throw e
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
                    _currentState.value =
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
                        _currentState.value = JoinKeysignState.JoinKeysign
                    }
                }
            }
        } else if (customPayloadId.isNotEmpty() && tempKeysignMessageProto != null) {
            viewModelScope.safeLaunch(
                onError = { e ->
                    Timber.e(e, "Failed to fetch custom message payload")
                    _currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
                }
            ) {
                if (fetchAndHandleCustomMessagePayload(_serverAddress)) {
                    _currentState.value = JoinKeysignState.JoinKeysign
                } else {
                    _currentState.value = JoinKeysignState.Error(JoinKeysignError.InvalidQr)
                }
            }
        } else {
            _currentState.value = JoinKeysignState.JoinKeysign
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

    /**
     * Drives the QBTC claim co-sign: flips into [JoinKeysignState.QbtcClaim], delegates the suspend
     * co-sign work to [qbtcClaimCosign], then surfaces the broadcast result. A missing Bitcoin/QBTC
     * account maps to [JoinKeysignError.MissingQbtcClaimAccount]; any other failure to
     * [JoinKeysignError.FailedConnectToServer].
     */
    private fun startQbtcClaimCosign() {
        if (!isJoiningKeysign.compareAndSet(false, true)) return
        _currentState.value = JoinKeysignState.QbtcClaim(txHash = null, totalSats = null)
        viewModelScope.safeLaunch(
            onError = { e ->
                Timber.e(e, "QBTC claim co-sign failed")
                val error =
                    when (e) {
                        is MissingQbtcClaimAccountException ->
                            JoinKeysignError.MissingQbtcClaimAccount
                        else -> JoinKeysignError.FailedConnectToServer
                    }
                _currentState.value = JoinKeysignState.Error(error)
            }
        ) {
            val result =
                qbtcClaimCosign(
                    vault = _currentVault,
                    serverUrl = _serverAddress,
                    sessionId = _sessionID,
                    encryptionKeyHex = _encryptionKeyHex,
                )
            _currentState.value =
                JoinKeysignState.QbtcClaim(txHash = result.txHash, totalSats = result.totalSats)
        }
    }

    fun joinKeysign() {
        if (!isJoiningKeysign.compareAndSet(false, true)) return
        viewModelScope.safeLaunch {
            withContext(Dispatchers.IO) {
                try {
                    Timber.tag("JoinKeysignViewModel").d("Joining keysign")
                    sessionApi.startSession(_serverAddress, _sessionID, listOf(_localPartyID))
                    // Set the waiting state before launching the poller: waitForKeysignToStart()
                    // starts its own coroutine that may publish Keysign immediately, and setting
                    // the
                    // state afterwards would overwrite that transition back to
                    // WaitingForKeysignStart.
                    _currentState.value = JoinKeysignState.WaitingForKeysignStart
                    waitForKeysignToStart()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: HttpException) {
                    Timber.tag("JoinKeysignViewModel")
                        .e(
                            "Failed to join keysign (HTTP %d): %s",
                            e.statusCode,
                            e.stackTraceToString(),
                        )
                    _currentState.value =
                        if (e.statusCode >= 500) {
                            JoinKeysignState.Error(JoinKeysignError.RelayUnavailable)
                        } else {
                            JoinKeysignState.Error(
                                JoinKeysignError.FailedToStart(e.message.toString())
                            )
                        }
                    isJoiningKeysign.set(false)
                } catch (e: Exception) {
                    Timber.tag("JoinKeysignViewModel")
                        .e("Failed to join keysign: %s", e.stackTraceToString())
                    _currentState.value =
                        JoinKeysignState.Error(JoinKeysignError.FailedToStart(e.message.toString()))
                    isJoiningKeysign.set(false)
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

                JoinKeysignError.RelayUnavailable,
                JoinKeysignError.Timeout -> {
                    _currentState.value = JoinKeysignState.JoinKeysign
                    joinKeysign()
                }

                else -> navigator.navigate(Destination.Back)
            }
        }
    }

    private fun cleanUp() {
        _jobWaitingForKeysignStart?.cancel()
        blockaidSimulationJob?.cancel()
    }

    private fun waitForKeysignToStart() {
        _jobWaitingForKeysignStart =
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    when (
                        val outcome =
                            awaitKeysignStart(
                                timeout = WAIT_FOR_KEYSIGN_START_TIMEOUT,
                                pollInterval = KEYSIGN_START_POLL_INTERVAL,
                                checkStarted = ::checkKeygenStarted,
                            )
                    ) {
                        KeysignStartOutcome.Started ->
                            _currentState.value = JoinKeysignState.Keysign

                        is KeysignStartOutcome.FailedToPrepare -> {
                            Timber.e("Failed to prepare messages to sign")
                            _currentState.value =
                                JoinKeysignState.Error(
                                    JoinKeysignError.FailedToCheck(outcome.message)
                                )
                        }

                        is KeysignStartOutcome.FailedToCheck -> {
                            Timber.e("Failed to check keysign start: %s", outcome.message)
                            _currentState.value =
                                JoinKeysignState.Error(
                                    JoinKeysignError.FailedToCheck(outcome.message)
                                )
                        }

                        KeysignStartOutcome.TimedOut -> {
                            Timber.w("Timed out waiting for the initiator to start the keysign")
                            // Allow tryAgain() to re-register and re-poll the session.
                            isJoiningKeysign.set(false)
                            _currentState.value = JoinKeysignState.Error(JoinKeysignError.Timeout)
                        }
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
                resolveMessagesToSign()
                return true
            }
        } catch (e: KeysignMessagesException) {
            throw e
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.e(e, "Failed to check keysign start")
            // Surface as a terminal outcome rather than a "not started yet" false, so a real
            // relay failure ends the wait instead of being masked by the timeout (issue #4856).
            throw KeysignCheckException(e.message ?: "Failed to check keysign start")
        }
        return false
    }

    private suspend fun resolveMessagesToSign() {
        try {
            when {
                _keysignPayload != null -> {
                    // The UTXOSpecific proto can't carry the live ZEC branch id, so a payload
                    // rebuilt from the initiator's QR/relay proto arrives with it null. Re-resolve
                    // it here (same network-global value the initiator used) before computing
                    // sighashes so this co-signer's ZIP-243 digest matches the rest of the
                    // committee.
                    val payload =
                        withZcashBranchId(
                            _keysignPayload ?: error("keysignPayload unexpectedly null")
                        )
                    _keysignPayload = payload
                    messagesToSign =
                        SigningHelper.getKeysignMessages(payload = payload, vault = _currentVault)
                }

                customMessagePayload != null -> {
                    val payload =
                        customMessagePayload ?: error("customMessagePayload unexpectedly null")
                    messagesToSign = SigningHelper.getKeysignMessages(payload)
                }

                else ->
                    throw KeysignMessagesException(
                        "Both keysign payload and custom message payload are null"
                    )
            }
        } catch (e: KeysignMessagesException) {
            throw e
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            throw KeysignMessagesException(e.message ?: "Failed to resolve messages to sign")
        }
    }

    /**
     * Stamps the live ZIP-243 branch id onto a Zcash UTXO payload rebuilt from proto. Returns the
     * payload unchanged for non-Zcash chains, non-UTXO specifics, or when the RPC is unreachable —
     * in the Zcash case that leaves the branch id null, so signing refuses (there is no compiled-in
     * fallback).
     */
    private suspend fun withZcashBranchId(payload: KeysignPayload): KeysignPayload {
        if (payload.coin.chain != Chain.Zcash) return payload
        val utxo = payload.blockChainSpecific as? BlockChainSpecific.UTXO ?: return payload
        val branchId = zcashApi.getConsensusBranchIdHex() ?: return payload
        return payload.copy(blockChainSpecific = utxo.copy(zcashBranchId = branchId))
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

    /**
     * Finishes the joined-device flow after a successful keysign. Clears the back stack so the
     * wrapping ViewModelStore (and the foreground status-polling service it owns) tears down
     * promptly — without this, navigating to home would leave the join destination on the stack and
     * the polling service would keep firing until its own timeout.
     */
    fun complete() {
        viewModelScope.safeLaunch {
            navigator.route(Route.Home(), NavigationOptions(clearBackStack = true))
        }
    }

    override fun onCleared() {
        cleanUp()
        super.onCleared()
    }
}
