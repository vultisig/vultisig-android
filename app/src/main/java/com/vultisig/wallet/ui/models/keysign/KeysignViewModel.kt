package com.vultisig.wallet.ui.models.keysign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.api.txstatus.TransactionResult
import com.vultisig.wallet.data.api.txstatus.TransactionStatusRepository
import com.vultisig.wallet.data.api.txstatus.TxStatusConfiguration
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.keygen.DKLSKeysign
import com.vultisig.wallet.data.keygen.SchnorrKeysign
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.tss.PullTssMessagesUseCase
import com.vultisig.wallet.data.utils.compatibleDerivationPath
import com.vultisig.wallet.ui.models.SendTxUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.KeysignResponse
import tss.ServiceImpl
import tss.Tss
import vultisig.keysign.v1.CustomMessagePayload
import java.math.BigInteger
import java.time.Instant
import java.util.Base64
import kotlin.time.Duration.Companion.seconds

internal sealed class KeysignState {
    data object CreatingInstance : KeysignState()
    data object KeysignECDSA : KeysignState()
    data object KeysignEdDSA : KeysignState()
    data class KeysignFinished(val status: TransactionStatus) : KeysignState()
    data class Error(val errorMessage: String) : KeysignState()
}

internal sealed interface TransactionTypeUiModel {
    data class Send(val tx: SendTxUiModel) : TransactionTypeUiModel
    data class Swap(val swapTransactionUiModel: SwapTransactionUiModel) : TransactionTypeUiModel
    data class Deposit(val depositTransactionUiModel: DepositTransactionUiModel) :
        TransactionTypeUiModel

    data class SignMessage(val model: SignMessageTransactionUiModel) : TransactionTypeUiModel
}

sealed interface TransactionStatus {
    data object Broadcasted : TransactionStatus
    data object Pending : TransactionStatus
    data object Confirmed : TransactionStatus
    data class Failed(val cause: UiText) : TransactionStatus
}


internal class KeysignViewModel(
    val vault: Vault,
    private val keysignCommittee: List<String>,
    private val serverUrl: String,
    private val sessionId: String,
    private val encryptionKeyHex: String,
    private val messagesToSign: List<String>,
    private val keyType: TssKeyType,
    private val keysignPayload: KeysignPayload?,
    private val customMessagePayload: CustomMessagePayload?,
    private val thorChainApi: ThorChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val broadcastTx: BroadcastTxUseCase,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val navigator: Navigator<Destination>,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
    val transactionTypeUiModel: TransactionTypeUiModel?,
    private val pullTssMessages: PullTssMessagesUseCase,
    private val isInitiatingDevice: Boolean,
    private val addressBookRepository: AddressBookRepository,
    private val transactionStatusRepository: TransactionStatusRepository,
) : ViewModel() {
    val currentState: MutableStateFlow<KeysignState> =
        MutableStateFlow(KeysignState.CreatingInstance)
    val txHash = MutableStateFlow("")
    val approveTxHash = MutableStateFlow("")
    val txLink = MutableStateFlow("")
    val approveTxLink = MutableStateFlow("")
    val swapProgressLink = MutableStateFlow<String?>(null)
    val showSaveToAddressBook = MutableStateFlow(false)

    private var tssInstance: ServiceImpl? = null
    private var tssMessenger: TssMessenger? = null
    private val localStateAccessor: LocalStateAccessor = LocalStateAccessor(vault)

    private var pullTssMessagesJob: Job? = null
    private val signatures: MutableMap<String, KeysignResponse> = mutableMapOf()
    private var featureFlag: FeatureFlagJson? = null

    private var isNavigateToHome: Boolean = false


    private var pollingTxStatusJob: Job? = null

    init {
        val sendTx = transactionTypeUiModel as? TransactionTypeUiModel.Send
        sendTx?.tx?.let { tx ->
            viewModelScope.launch {
                val isSavedBefore = addressBookRepository.entryExists(
                    address = tx.dstAddress,
                    chainId = tx.token.token.chain.id
                )

                showSaveToAddressBook.value = isSavedBefore.not()
            }
        }
    }

    fun startKeysign() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (vault.libType) {
                    SigningLibType.GG20 ->
                        signAndBroadcast()

                    SigningLibType.DKLS ->
                        startKeysignDkls()

                    SigningLibType.KeyImport ->
                        TODO("Add KeyImport Signing logic")
                }
            }
        }
    }

    private suspend fun startKeysignDkls() {
        if (keysignPayload == null && customMessagePayload == null) {
            error("Keysign payload is null")
        }
        try {
            when (keyType) {
                TssKeyType.ECDSA -> {
                    currentState.value = KeysignState.KeysignECDSA

                    val dkls = DKLSKeysign(
                        vault = vault,
                        keysignCommittee = keysignCommittee,
                        mediatorURL = serverUrl,
                        sessionID = sessionId,
                        encryptionKeyHex = encryptionKeyHex,
                        messageToSign = messagesToSign,
                        chainPath = this.keysignPayload?.coin?.coinType?.compatibleDerivationPath()
                            ?: "m/44'/60'/0'/0/0",
                        isInitiateDevice = isInitiatingDevice,
                        sessionApi = sessionApi,
                        encryption = encryption,
                    )

                    dkls.keysignWithRetry()

                    this.signatures += dkls.signatures
                    if (signatures.isEmpty()) {
                        error("Failed to sign transaction, signatures empty")
                    }
                    calculateCustomMessageSignature(this.signatures.values.first())
                }

                TssKeyType.EDDSA -> {
                    currentState.value = KeysignState.KeysignEdDSA

                    val schnorr = SchnorrKeysign(
                        vault = vault,
                        keysignCommittee = keysignCommittee,
                        mediatorURL = serverUrl,
                        sessionID = sessionId,
                        encryptionKeyHex = encryptionKeyHex,
                        messageToSign = messagesToSign,
                        isInitiateDevice = isInitiatingDevice,
                        sessionApi = sessionApi,
                        encryption = encryption,
                    )

                    schnorr.keysignWithRetry()

                    this.signatures += schnorr.signatures

                    if (signatures.isEmpty()) {
                        error("Failed to sign transaction, signatures empty")
                    }
                }
            }

            Timber.d("All messages signed, broadcasting transaction")
            if (!skipBroadcast()) {
                broadcastTransaction()
                checkThorChainTxResult()
            }
            isNavigateToHome = true
        } catch (e: Exception) {
            Timber.e(e)
            currentState.value = KeysignState.Error(e.message ?: "Unknown error")
        }
    }

    private fun skipBroadcast(): Boolean {
        val flag = keysignPayload?.skipBroadcast ?: false
        Timber.d("SkipBroadcastFlag, value: $flag")
        return flag
    }

    @Suppress("ReplaceNotNullAssertionWithElvisReturn")
    private suspend fun signAndBroadcast() {
        Timber.d("Start to SignAndBroadcast")
        currentState.value = KeysignState.CreatingInstance
        try {
            featureFlag = featureFlagApi.getFeatureFlag()
            val isEncryptionGcm = featureFlag?.isEncryptGcmEnabled == true

            tssMessenger = TssMessenger(
                serverUrl,
                sessionId,
                encryptionKeyHex,
                sessionApi,
                viewModelScope,
                encryption,
                isEncryptionGcm,
            )
            tssInstance = Tss.newService(tssMessenger, localStateAccessor, false)
                ?: error("Failed to create TSS instance")

            messagesToSign.forEach { message ->
                Timber.d("signing message: $message")
                signMessageWithRetry(tssInstance!!, message, 1)
            }

            Timber.d("All messages signed, broadcasting transaction")

            broadcastTransaction()
            checkThorChainTxResult()

            isNavigateToHome = true

            pullTssMessagesJob?.cancel()
        } catch (e: Exception) {
            Timber.e(e)
            currentState.value = KeysignState.Error(e.message ?: "Unknown error")
        }
    }

    private suspend fun checkThorChainTxResult() {
        val chainSpecific = keysignPayload?.blockChainSpecific
        if (chainSpecific !is BlockChainSpecific.THORChain)
            return
        if (!chainSpecific.isDeposit)
            return
        val transactionDetail = thorChainApi.getTransactionDetail(txHash.value)

        // https://docs.cosmos.network/v0.46/building-modules/errors.html#registration
        if (transactionDetail.code != null && !transactionDetail.codeSpace.isNullOrBlank()) {
            throw Exception(transactionDetail.rawLog)
        }
    }

    private suspend fun signMessageWithRetry(service: ServiceImpl, message: String, attempt: Int) {
        val keysignVerify = KeysignVerify(serverUrl, sessionId, sessionApi)
        try {
            Timber.d("signMessageWithRetry: $message, attempt: $attempt")
            val msgHash = message.md5()
            this.tssMessenger?.setMessageID(msgHash)
            Timber.d("signMessageWithRetry: msgHash: $msgHash")

            val isEncryptionGcm = featureFlag?.isEncryptGcmEnabled == true
            pullTssMessagesJob = viewModelScope.launch {
                pullTssMessages(
                    serverUrl = serverUrl,
                    sessionId = sessionId,
                    localPartyId = vault.localPartyID,
                    hexEncryptionKey = encryptionKeyHex,
                    isEncryptionGcm = isEncryptionGcm,
                    messageId = msgHash,
                    service = tssInstance!!,
                ).collect()
            }

            val keysignReq = tss.KeysignRequest()
            keysignReq.localPartyKey = vault.localPartyID
            keysignReq.keysignCommitteeKeys = keysignCommittee.joinToString(",")
            keysignReq.messageToSign = Base64.getEncoder().encodeToString(message.toHexBytes())
            keysignReq.derivePath =
                keysignPayload?.coin?.coinType?.compatibleDerivationPath() ?: "m/44'/60'/0'/0/0"

            val keysignResp = when (keyType) {
                TssKeyType.ECDSA -> {
                    keysignReq.pubKey = vault.pubKeyECDSA
                    currentState.value = KeysignState.KeysignECDSA
                    service.keysignECDSA(keysignReq)
                }

                TssKeyType.EDDSA -> {
                    keysignReq.pubKey = vault.pubKeyEDDSA
                    currentState.value = KeysignState.KeysignEdDSA
                    service.keysignEdDSA(keysignReq)
                }
            }
            if (keysignResp.r.isNullOrEmpty() || keysignResp.s.isNullOrEmpty()) {
                throw Exception("Failed to sign message")
            }
            calculateCustomMessageSignature(keysignResp)
            this.signatures[message] = keysignResp
            keysignVerify.markLocalPartyKeysignComplete(message, keysignResp)

            pullTssMessagesJob?.cancel()

            delay(1.seconds)
        } catch (e: Exception) {
            pullTssMessagesJob?.cancel()
            Timber.tag("KeysignViewModel")
                .d("signMessageWithRetry error: %s", e.stackTraceToString())
            val resp = keysignVerify.checkKeysignComplete(message)
            resp?.let {
                this.signatures[message] = it
                return
            }
            if (attempt > 3) {
                throw e
            }
            signMessageWithRetry(service, message, attempt + 1)
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun calculateCustomMessageSignature(keysignResp: KeysignResponse) {
        if (customMessagePayload == null)
            return
        txHash.value = keysignResp.getSignature().toHexString()
    }

    private suspend fun broadcastTransaction() {
        val payload = keysignPayload ?: return

        var nonceAcc = BigInteger.ZERO

        val approvePayload = payload.approvePayload
        val chain = payload.coin.chain
        if (approvePayload != null) {
            val signedApproveTransaction = THORChainSwaps(vault.pubKeyECDSA, vault.hexChainCode)
                .getSignedApproveTransaction(
                    approvePayload,
                    payload,
                    signatures
                )

            val evmApi = evmApiFactory.createEvmApi(chain)
            approveTxHash.value = evmApi.sendTransaction(signedApproveTransaction.rawTransaction)

            nonceAcc++
        }

        val signedTx = SigningHelper.getSignedTransaction(
            keysignPayload = payload,
            vault = vault,
            signatures = signatures,
            nonceAcc = nonceAcc
        )

        val txHash = broadcastTx(
            chain = chain,
            tx = signedTx,
        )

        Timber.d("transaction hash: $txHash")
        if (txHash != null) {
            this.txHash.value = txHash
            txLink.value = explorerLinkRepository.getTransactionLink(chain, txHash)
            swapProgressLink.value =
                explorerLinkRepository.getSwapProgressLink(txHash, payload.swapPayload)

            updateTransactionStatus(txHash, chain)
        }
        if (approveTxHash.value.isNotEmpty()) {
            approveTxLink.value =
                explorerLinkRepository.getTransactionLink(chain, approveTxHash.value)
        }
    }

    fun updateTransactionStatus(txHash: String, chain: Chain) {
        val txStatusConfiguration = TxStatusConfiguration.getConfig(chain)
        if (pollingTxStatusJob?.isActive == false)
            return

        pollingTxStatusJob = viewModelScope.launch {
            currentState.value =
                KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
            val startTime = Instant.now()
            while (true) {
                try {
                    val result = transactionStatusRepository.checkTransactionStatus(txHash, chain)

                    when (result) {
                        is TransactionResult.Confirmed -> {
                            currentState.value =
                                KeysignState.KeysignFinished(TransactionStatus.Confirmed)
                            break
                        }

                        is TransactionResult.Failed -> {
                            currentState.value =
                                KeysignState.KeysignFinished(
                                    TransactionStatus.Failed(
                                        cause = result.reason.asUiText()
                                    )
                                )
                            break
                        }

                        is TransactionResult.Pending -> {
                            currentState.value =
                                KeysignState.KeysignFinished(TransactionStatus.Pending)
                        }

                        is TransactionResult.NotFound -> {
                            val elapsed = java.time.Duration.between(
                                startTime,
                                Instant.now()
                            ).toMillis()

                            if (elapsed > txStatusConfiguration.maxWaitMs) {
                                currentState.value =
                                    KeysignState.KeysignFinished(
                                        status = TransactionStatus.Failed(
                                            cause = "Confirmation taking longer than expected".asUiText()
                                        )
                                    )
                                break
                            }
                        }
                    }

                    delay(txStatusConfiguration.pollIntervalMs)

                } catch (_: Exception) {
                    delay(txStatusConfiguration.pollIntervalMs)
                }
            }
        }
    }

    fun stopPolling() {
        pollingTxStatusJob?.cancel()
        pollingTxStatusJob = null
    }

    fun navigateToHome() {
        viewModelScope.launch {
            if (isNavigateToHome) {
                navigator.route(
                    Route.Home(),
                    NavigationOptions(
                        clearBackStack = true
                    )
                )
            } else {
                navigator.navigate(Destination.Back)
            }
        }
    }

    fun navigateToAddressBook() {
        val sendTx = transactionTypeUiModel as? TransactionTypeUiModel.Send
        sendTx?.tx?.let { tx ->
            viewModelScope.launch {
                navigator.route(
                    Route.AddressEntry(
                        vaultId = vault.id,
                        address = tx.dstAddress,
                        chainId = tx.token.token.chain.id
                    )
                )
            }
        }
    }

    override fun onCleared() {
        stopPolling()
        super.onCleared()
    }
}
