package com.vultisig.wallet.ui.models.keysign

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.chains.helpers.SigningHelper
import com.vultisig.wallet.data.chains.helpers.THORChainSwaps
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.db.models.TransactionStatus.BROADCASTED
import com.vultisig.wallet.data.db.models.TransactionType
import com.vultisig.wallet.data.keygen.DKLSKeysign
import com.vultisig.wallet.data.keygen.MldsaKeysign
import com.vultisig.wallet.data.keygen.SchnorrKeysign
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.CommonTransactionHistoryData
import com.vultisig.wallet.data.models.SendTransactionHistoryData
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.UnknownTransactionHistoryData
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getEcdsaSigningKey
import com.vultisig.wallet.data.models.getEddsaSigningKey
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.services.TransactionStatusServiceManager
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.tss.PullTssMessagesUseCase
import com.vultisig.wallet.data.usecases.txstatus.TransactionResult
import com.vultisig.wallet.data.usecases.txstatus.TxStatusConfigurationProvider
import com.vultisig.wallet.data.utils.compatibleDerivationPath
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.TransactionDetailsUiModel
import com.vultisig.wallet.ui.models.deposit.DepositTransactionUiModel
import com.vultisig.wallet.ui.models.sign.SignMessageTransactionUiModel
import com.vultisig.wallet.ui.models.swap.SwapTransactionUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.normalizeAddressForLookup
import com.vultisig.wallet.ui.utils.or
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.math.BigInteger
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.KeysignResponse
import tss.ServiceImpl
import tss.Tss
import vultisig.keysign.v1.CustomMessagePayload

private const val DEFAULT_ETHEREUM_DERIVATION_PATH = "m/44'/60'/0'/0/0"

internal sealed class KeysignState {
    data object CreatingInstance : KeysignState()

    data object KeysignECDSA : KeysignState()

    data object KeysignEdDSA : KeysignState()

    data object KeysignMLDSA : KeysignState()

    data class KeysignFinished(val transactionStatus: TransactionStatus) : KeysignState()

    data class Error(val errorMessage: UiText) : KeysignState()

    /** Emitted when no messages have been received from [missingPeers] for ~10 s. */
    data class WaitingForPeer(val missingPeers: List<String>) : KeysignState()

    val isInProgress: Boolean
        get() =
            when (this) {
                CreatingInstance,
                KeysignECDSA,
                KeysignEdDSA,
                KeysignMLDSA,
                is WaitingForPeer -> true
                is KeysignFinished,
                is Error -> false
            }
}

internal val KeysignState.progress: Float
    get() =
        when (this) {
            is KeysignState.CreatingInstance -> 0.0f
            is KeysignState.KeysignECDSA -> 0.33f
            is KeysignState.KeysignEdDSA -> 0.66f
            // EdDSA and MLDSA are mutually exclusive signing paths, so both map to 66%
            is KeysignState.KeysignMLDSA -> 0.66f
            is KeysignState.KeysignFinished -> 1f
            // Dead code: Error state is rendered by a separate branch in KeysignView
            is KeysignState.Error -> 0f
            is KeysignState.WaitingForPeer -> 0.33f
        }

internal sealed interface TransactionTypeUiModel {
    data class Send(val tx: TransactionDetailsUiModel) : TransactionTypeUiModel

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

internal class KeysignViewModel
@AssistedInject
constructor(
    @Assisted val vault: Vault,
    @Assisted("keysignCommittee") private val keysignCommittee: List<String>,
    @Assisted("serverUrl") private val serverUrl: String,
    @Assisted("sessionId") private val sessionId: String,
    @Assisted("encryptionKeyHex") private val encryptionKeyHex: String,
    @Assisted("messagesToSign") private val messagesToSign: List<String>,
    @Assisted private val keyType: TssKeyType,
    @Assisted private val keysignPayload: KeysignPayload?,
    @Assisted private val customMessagePayload: CustomMessagePayload?,
    @Assisted val transactionTypeUiModel: TransactionTypeUiModel?,
    @Assisted private val isInitiatingDevice: Boolean,
    @Assisted private val transactionHistoryData: TransactionHistoryData?,
    private val thorChainApi: ThorChainApi,
    private val evmApiFactory: EvmApiFactory,
    private val broadcastTx: BroadcastTxUseCase,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val navigator: Navigator<Destination>,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
    private val pullTssMessages: PullTssMessagesUseCase,
    private val addressBookRepository: AddressBookRepository,
    private val txStatusConfigurationProvider: TxStatusConfigurationProvider,
    private val transactionStatusServiceManager: TransactionStatusServiceManager,
    private val vaultRepository: VaultRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val balanceRepository: BalanceRepository,
) : ViewModel() {

    /** Creates [KeysignViewModel] with runtime-provided assisted parameters. */
    @AssistedFactory
    interface Factory {
        fun create(
            vault: Vault,
            @Assisted("keysignCommittee") keysignCommittee: List<String>,
            @Assisted("serverUrl") serverUrl: String,
            @Assisted("sessionId") sessionId: String,
            @Assisted("encryptionKeyHex") encryptionKeyHex: String,
            @Assisted("messagesToSign") messagesToSign: List<String>,
            keyType: TssKeyType,
            keysignPayload: KeysignPayload?,
            customMessagePayload: CustomMessagePayload?,
            transactionTypeUiModel: TransactionTypeUiModel?,
            isInitiatingDevice: Boolean,
            transactionHistoryData: TransactionHistoryData?,
        ): KeysignViewModel
    }

    val currentState: MutableStateFlow<KeysignState> =
        MutableStateFlow(KeysignState.CreatingInstance)

    val txHash = MutableStateFlow("")
    val approveTxHash = MutableStateFlow("")
    val txLink = MutableStateFlow("")
    val approveTxLink = MutableStateFlow("")
    val swapProgressLink = MutableStateFlow<String?>(null)
    val showSaveToAddressBook = MutableStateFlow(false)
    val resolvedTransactionUiModel = MutableStateFlow(transactionTypeUiModel)

    private var tssInstance: ServiceImpl? = null
    private var tssMessenger: TssMessenger? = null
    private val localStateAccessor: LocalStateAccessor = LocalStateAccessor(vault)

    private var pullTssMessagesJob: Job? = null
    private val signatures: MutableMap<String, KeysignResponse> = mutableMapOf()
    private var featureFlags: FeatureFlagJson? = null

    private var isNavigateToHome: Boolean = false

    private var pollingTxStatusJob: Job? = null

    init {
        val sendTx = transactionTypeUiModel as? TransactionTypeUiModel.Send
        sendTx?.tx?.let { tx ->
            viewModelScope.safeLaunch {
                val chain = tx.token.token.chain
                val allVaults = withContext(Dispatchers.IO) { vaultRepository.getAll() }
                val normalizedDstAddress = normalizeAddressForLookup(tx.dstAddress)
                val dstVaultName =
                    allVaults
                        .firstOrNull { v ->
                            v.coins.any {
                                it.chain == chain &&
                                    normalizeAddressForLookup(it.address) == normalizedDstAddress
                            }
                        }
                        ?.name

                val isSavedBefore =
                    addressBookRepository.entryExists(address = tx.dstAddress, chainId = chain.id)

                showSaveToAddressBook.value = isSavedBefore.not() && dstVaultName == null

                val dstAddressBookTitle =
                    if (dstVaultName == null && isSavedBefore) {
                        runCatching {
                                addressBookRepository.getEntry(chain.id, tx.dstAddress).title
                            }
                            .getOrNull()
                    } else null

                resolvedTransactionUiModel.value =
                    TransactionTypeUiModel.Send(
                        tx.copy(
                            srcVaultName = vault.name,
                            dstVaultName = dstVaultName,
                            dstAddressBookTitle = dstAddressBookTitle,
                        )
                    )
            }
        }
    }

    fun startKeysign() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                when (vault.libType) {
                    SigningLibType.GG20 -> signAndBroadcast()

                    SigningLibType.DKLS -> startKeysignDkls()

                    SigningLibType.KeyImport -> startKeysignKeyImport()
                }
            }
        }
    }

    private suspend fun startKeysignDkls() {
        startKeysignCore(
            ecdsaPublicKeyOverride = null,
            eddsaPublicKeyOverride = null,
            chainPath =
                keysignPayload?.coin?.coinType?.compatibleDerivationPath()
                    ?: DEFAULT_ETHEREUM_DERIVATION_PATH,
        )
    }

    /**
     * KeyImport signing: uses per-chain public keys and empty derivation path. The per-chain
     * keyshare already corresponds to the derived key, so no BIP32 derivation is needed during
     * signing (chainPath = "").
     */
    private suspend fun startKeysignKeyImport() {
        val chain = keysignPayload?.coin?.chain
        startKeysignCore(
            ecdsaPublicKeyOverride =
                if (chain != null) vault.getEcdsaSigningKey(chain).publicKey else vault.pubKeyECDSA,
            eddsaPublicKeyOverride =
                if (chain != null) vault.getEddsaSigningKey(chain) else vault.pubKeyEDDSA,
            chainPath = "",
        )
    }

    private suspend fun startKeysignCore(
        ecdsaPublicKeyOverride: String?,
        eddsaPublicKeyOverride: String?,
        chainPath: String,
    ) {
        try {
            if (keysignPayload == null && customMessagePayload == null) {
                error("Keysign payload is null")
            }
            when (keyType) {
                TssKeyType.ECDSA -> {
                    currentState.value = KeysignState.KeysignECDSA

                    val dkls =
                        DKLSKeysign(
                            vault = vault,
                            keysignCommittee = keysignCommittee,
                            mediatorURL = serverUrl,
                            sessionID = sessionId,
                            encryptionKeyHex = encryptionKeyHex,
                            messageToSign = messagesToSign,
                            chainPath = chainPath,
                            isInitiateDevice = isInitiatingDevice,
                            publicKeyOverride = ecdsaPublicKeyOverride,
                            sessionApi = sessionApi,
                            encryption = encryption,
                            onWaitingForPeers = { missingPeers ->
                                currentState.value = KeysignState.WaitingForPeer(missingPeers)
                            },
                        )

                    dkls.keysignWithRetry()

                    this.signatures += dkls.signatures
                    if (signatures.isEmpty()) {
                        error("Failed to sign transaction, signatures empty")
                    }
                }

                TssKeyType.EDDSA -> {
                    currentState.value = KeysignState.KeysignEdDSA

                    val schnorr =
                        SchnorrKeysign(
                            vault = vault,
                            keysignCommittee = keysignCommittee,
                            mediatorURL = serverUrl,
                            sessionID = sessionId,
                            encryptionKeyHex = encryptionKeyHex,
                            messageToSign = messagesToSign,
                            isInitiateDevice = isInitiatingDevice,
                            publicKeyOverride = eddsaPublicKeyOverride,
                            sessionApi = sessionApi,
                            encryption = encryption,
                        )

                    schnorr.keysignWithRetry()

                    this.signatures += schnorr.signatures

                    if (signatures.isEmpty()) {
                        error("Failed to sign transaction, signatures empty")
                    }
                }

                TssKeyType.MLDSA -> {
                    currentState.value = KeysignState.KeysignMLDSA

                    Timber.d(
                        "MLDSA keysign: pubKeyMLDSA=%s..., keyshares=%s, isInitiating=%b",
                        vault.pubKeyMLDSA.take(20),
                        vault.keyshares.map { it.pubKey.take(20) },
                        isInitiatingDevice,
                    )

                    val mldsa =
                        MldsaKeysign(
                            keysignCommittee = keysignCommittee,
                            mediatorURL = serverUrl,
                            sessionID = sessionId,
                            messageToSign = messagesToSign,
                            vault = vault,
                            encryptionKeyHex = encryptionKeyHex,
                            isInitiateDevice = isInitiatingDevice,
                            sessionApi = sessionApi,
                            encryption = encryption,
                        )

                    mldsa.keysignWithRetry()

                    this.signatures += mldsa.signatures

                    if (signatures.isEmpty()) {
                        error("Failed to sign transaction, signatures empty")
                    }
                }
            }

            Timber.d("All messages signed, broadcasting transaction")
            if (customMessagePayload != null) {
                require(messagesToSign.isNotEmpty()) {
                    "messagesToSign must not be empty when extracting custom message"
                }
                val customMessageKey = messagesToSign.first()
                val customMessageResp =
                    signatures[customMessageKey]
                        ?: error("No signature found for custom message $customMessageKey")
                calculateCustomMessageSignature(customMessageResp)
            }
            if (!skipBroadcast()) {
                broadcastTransaction()
                checkThorChainTxResult()
            }
            if (customMessagePayload != null) {
                // For custom message signing, we consider the flow complete after signing without
                // broadcasting
                currentState.value = KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
            }
            isNavigateToHome = true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            currentState.value = KeysignState.Error(e.message or R.string.unknown_error)
        }
    }

    private fun skipBroadcast(): Boolean {
        val flag = keysignPayload?.skipBroadcast ?: false
        Timber.d("SkipBroadcastFlag, value: $flag")
        return flag
    }

    private suspend fun signAndBroadcast() {
        Timber.d("Start to SignAndBroadcast")
        currentState.value = KeysignState.CreatingInstance
        try {
            featureFlags = featureFlagApi.getFeatureFlags()
            val isEncryptionGcm = featureFlags?.isEncryptGcmEnabled == true

            tssMessenger =
                TssMessenger(
                    serverUrl,
                    sessionId,
                    encryptionKeyHex,
                    sessionApi,
                    viewModelScope,
                    encryption,
                    isEncryptionGcm,
                )
            val tssService =
                Tss.newService(tssMessenger, localStateAccessor, false)
                    ?: error("Failed to create TSS instance")
            tssInstance = tssService

            messagesToSign.forEach { message ->
                Timber.d("signing message: $message")
                signMessageWithRetry(tssService, message, 1)
            }

            Timber.d("All messages signed, broadcasting transaction")

            broadcastTransaction()
            checkThorChainTxResult()
            if (customMessagePayload != null) {
                // For custom message signing, we consider the flow complete after signing without
                // broadcasting
                currentState.value = KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
            }
            isNavigateToHome = true

            pullTssMessagesJob?.cancel()
        } catch (e: CancellationException) {
            pullTssMessagesJob?.cancel()
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            currentState.value = KeysignState.Error(e.message or R.string.unknown_error)
        }
    }

    private suspend fun checkThorChainTxResult() {
        val chainSpecific = keysignPayload?.blockChainSpecific
        if (chainSpecific !is BlockChainSpecific.THORChain) return
        if (!chainSpecific.isDeposit) return
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

            val isEncryptionGcm = featureFlags?.isEncryptGcmEnabled == true
            pullTssMessagesJob =
                viewModelScope.launch {
                    pullTssMessages(
                            serverUrl = serverUrl,
                            sessionId = sessionId,
                            localPartyId = vault.localPartyID,
                            hexEncryptionKey = encryptionKeyHex,
                            isEncryptionGcm = isEncryptionGcm,
                            messageId = msgHash,
                            service = service,
                        )
                        .collect()
                }

            val keysignReq = tss.KeysignRequest()
            keysignReq.localPartyKey = vault.localPartyID
            keysignReq.keysignCommitteeKeys = keysignCommittee.joinToString(",")
            keysignReq.messageToSign = Base64.getEncoder().encodeToString(message.toHexBytes())
            keysignReq.derivePath =
                keysignPayload?.coin?.coinType?.compatibleDerivationPath()
                    ?: DEFAULT_ETHEREUM_DERIVATION_PATH

            val keysignResp =
                when (keyType) {
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

                    TssKeyType.MLDSA -> {
                        error("MLDSA is not supported in legacy TSS signing")
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
        } catch (e: CancellationException) {
            pullTssMessagesJob?.cancel()
            throw e
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
        if (customMessagePayload == null) return
        txHash.value = keysignResp.getSignature().toHexString()
    }

    private suspend fun broadcastTransaction() {
        val payload = keysignPayload ?: return

        var nonceAcc = BigInteger.ZERO

        val approvePayload = payload.approvePayload
        val chain = payload.coin.chain
        if (approvePayload != null) {
            val (approveKey, approveChainCode) = vault.getEcdsaSigningKey(chain)
            val signedApproveTransaction =
                THORChainSwaps(approveKey, approveChainCode, vault.getEddsaSigningKey(chain))
                    .getSignedApproveTransaction(approvePayload, payload, signatures)

            val evmApi = evmApiFactory.createEvmApi(chain)
            approveTxHash.value = evmApi.sendTransaction(signedApproveTransaction.rawTransaction)

            nonceAcc++
        }

        val signedTx =
            SigningHelper.getSignedTransaction(
                keysignPayload = payload,
                vault = vault,
                signatures = signatures,
                nonceAcc = nonceAcc,
            )

        val txHash = broadcastTx(chain = chain, tx = signedTx)

        Timber.d("transaction hash: $txHash")
        if (txHash != null) {
            this.txHash.value = txHash
            txLink.value = explorerLinkRepository.getTransactionLink(chain, txHash)
            swapProgressLink.value =
                explorerLinkRepository.getSwapProgressLink(txHash, payload.swapPayload)
            runCatching { balanceRepository.invalidateBalance(payload.coin.address, payload.coin) }
                .onFailure { Timber.e(it, "Failed to invalidate balance cache after broadcast") }
            runCatching {
                    balanceRepository.invalidateDeFiBalance(
                        address = payload.coin.address,
                        chain = chain,
                        vaultId = vault.id,
                    )
                }
                .onFailure {
                    Timber.e(it, "Failed to invalidate DeFi balance cache after broadcast")
                }
            saveTransactionHistory(txHash, chain)
            if (txStatusConfigurationProvider.supportTxStatus(chain)) {
                startForegroundPolling(txHash, chain)
            } else {
                currentState.value = KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
            }
        }
        if (approveTxHash.value.isNotEmpty()) {
            approveTxLink.value =
                explorerLinkRepository.getTransactionLink(chain, approveTxHash.value)
        }
    }

    private suspend fun saveTransactionHistory(txHash: String, chain: Chain) {
        transactionHistoryData?.let {
            runCatching {
                val now = System.currentTimeMillis()
                val historyData =
                    CommonTransactionHistoryData(
                        vaultId = vault.id,
                        txHash = txHash,
                        chain = chain.raw,
                        timestamp = now,
                        explorerUrl = txLink.value,
                        status = BROADCASTED,
                        type =
                            when (it) {
                                is SendTransactionHistoryData -> TransactionType.SEND
                                is SwapTransactionHistoryData -> TransactionType.SWAP
                                is UnknownTransactionHistoryData -> return@runCatching
                            },
                        confirmedAt = null,
                        failureReason = null,
                        lastCheckedAt = now,
                    )
                transactionHistoryRepository.recordTransaction(
                    vaultId = vault.id,
                    txHash = txHash,
                    txData = it,
                    genericData = historyData,
                )
            }
        }
    }

    private fun startForegroundPolling(txHash: String, chain: Chain) {
        pollingTxStatusJob?.cancel()

        transactionStatusServiceManager.startPolling(txHash, chain)

        pollingTxStatusJob =
            viewModelScope.launch {
                currentState.value =
                    KeysignState.KeysignFinished(transactionStatus = TransactionStatus.Pending)
                transactionStatusServiceManager.serviceReady
                    .filter { it } // Wait until service is ready
                    .first()
                transactionStatusServiceManager.getStatusFlow()?.collect { statusResult ->
                    runCatching {
                            transactionHistoryRepository.updateTransactionStatus(
                                txHash,
                                statusResult,
                            )
                        }
                        .onFailure {
                            Timber.w(it, "Failed to update tx history status for %s", txHash)
                        }
                    currentState.value =
                        KeysignState.KeysignFinished(
                            transactionStatus = statusResult.toTransactionStatus()
                        )
                    when (statusResult) {
                        TransactionResult.NotFound,
                        is TransactionResult.Failed,
                        TransactionResult.Confirmed -> {
                            transactionStatusServiceManager.stopPolling()
                            pollingTxStatusJob?.cancel()
                        }

                        else -> Unit
                    }
                }
            }
    }

    fun stopPolling() {
        pollingTxStatusJob?.cancel()
        transactionStatusServiceManager.stopPolling()
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

    fun navigateToAddressBook() {
        val sendTx = transactionTypeUiModel as? TransactionTypeUiModel.Send
        sendTx?.tx?.let { tx ->
            viewModelScope.launch {
                navigator.route(
                    Route.AddressEntry(
                        vaultId = vault.id,
                        address = tx.dstAddress,
                        chainId = tx.token.token.chain.id,
                    )
                )
            }
        }
    }

    private fun TransactionResult.toTransactionStatus() =
        when (this) {
            TransactionResult.Confirmed -> TransactionStatus.Confirmed
            is TransactionResult.Failed -> TransactionStatus.Failed(this.reason.asUiText())
            TransactionResult.NotFound ->
                TransactionStatus.Failed("Confirmation taking longer than expected".asUiText())

            TransactionResult.Pending -> TransactionStatus.Pending
        }

    override fun onCleared() {
        stopPolling()
        transactionStatusServiceManager.cleanup()
        super.onCleared()
    }
}
