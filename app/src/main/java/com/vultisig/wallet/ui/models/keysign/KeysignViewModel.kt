package com.vultisig.wallet.ui.models.keysign

import androidx.annotation.DrawableRes
import androidx.annotation.VisibleForTesting
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.EvmApiFactory
import com.vultisig.wallet.data.api.FeatureFlagApi
import com.vultisig.wallet.data.api.KeysignVerify
import com.vultisig.wallet.data.api.SessionApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.FeatureFlagJson
import com.vultisig.wallet.data.common.md5
import com.vultisig.wallet.data.common.toHexBytes
import com.vultisig.wallet.data.keygen.DKLSKeysign
import com.vultisig.wallet.data.keygen.MldsaKeysign
import com.vultisig.wallet.data.keygen.SchnorrKeysign
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.SwapProvider
import com.vultisig.wallet.data.models.SwapTransactionHistoryData
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TransactionHistoryData
import com.vultisig.wallet.data.models.TssKeyType
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getEcdsaSigningKey
import com.vultisig.wallet.data.models.getEddsaSigningKey
import com.vultisig.wallet.data.models.getSwapProviderId
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.DAppMetadata
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.tokenLogoRes
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.TransactionHistoryRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.services.KeysignTxStatusPoller
import com.vultisig.wallet.data.tss.LocalStateAccessor
import com.vultisig.wallet.data.tss.TssMessenger
import com.vultisig.wallet.data.tss.getSignature
import com.vultisig.wallet.data.tss.getSignatureWithRecoveryID
import com.vultisig.wallet.data.usecases.AwaitApprovalConfirmationUseCase
import com.vultisig.wallet.data.usecases.BroadcastKeysignUseCase
import com.vultisig.wallet.data.usecases.BroadcastTxUseCase
import com.vultisig.wallet.data.usecases.Encryption
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.KeysignBroadcastResult
import com.vultisig.wallet.data.usecases.SaveKeysignTransactionHistoryUseCase
import com.vultisig.wallet.data.usecases.UpdateEvmActualFeeUseCase
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
import com.vultisig.wallet.ui.utils.or
import com.vultisig.wallet.ui.utils.resolveDstVaultName
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import java.util.*
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import tss.KeysignResponse
import tss.ServiceImpl
import tss.Tss
import vultisig.keysign.v1.CustomMessagePayload

private const val DEFAULT_ETHEREUM_DERIVATION_PATH = "m/44'/60'/0'/0/0"

/** UI state for an in-progress or completed keysign session. */
internal sealed class KeysignState {
    /** Initial state while the native signing instance is being constructed. */
    data object CreatingInstance : KeysignState()

    /** Active state while ECDSA (DKLS) signing messages are being exchanged. */
    data object KeysignECDSA : KeysignState()

    /** Active state while EdDSA (Schnorr) signing messages are being exchanged. */
    data object KeysignEdDSA : KeysignState()

    /** Active state while ML-DSA (Dilithium) signing messages are being exchanged. */
    data object KeysignMLDSA : KeysignState()

    /**
     * Emitted when a keysign peer has been silent for ~10 s.
     *
     * @property missingPeers Party IDs that have not sent any messages in the current attempt.
     * @property signingProgress Rive progress value at the time silence was detected (0.33 for
     *   ECDSA, 0.66 for EdDSA/MLDSA); used to avoid animating the progress indicator backward.
     */
    data class WaitingForPeer(val missingPeers: List<String>, val signingProgress: Float = 0.33f) :
        KeysignState()

    /** Terminal state when signing and broadcast have completed. */
    data class KeysignFinished(val transactionStatus: TransactionStatus) : KeysignState()

    /** Terminal state when signing failed with an unrecoverable error. */
    data class Error(val errorMessage: UiText) : KeysignState()

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
            is KeysignState.WaitingForPeer -> this.signingProgress
            is KeysignState.KeysignFinished -> 1f
            else -> 0f
        }

/** Discriminated union of transaction types shown in the keysign confirmation UI. */
internal sealed interface TransactionTypeUiModel {
    /** A coin-transfer transaction. */
    data class Send(val tx: TransactionDetailsUiModel) : TransactionTypeUiModel

    /** A cross-chain or DEX swap. */
    data class Swap(val swapTransactionUiModel: SwapTransactionUiModel) : TransactionTypeUiModel

    /** A protocol deposit (e.g. THORChain, Maya). */
    data class Deposit(val depositTransactionUiModel: DepositTransactionUiModel) :
        TransactionTypeUiModel

    /** A custom message signing request. */
    data class SignMessage(val model: SignMessageTransactionUiModel) : TransactionTypeUiModel
}

/** Lifecycle status of a broadcast transaction as observed by the polling service. */
sealed interface TransactionStatus {
    /** Transaction has been submitted to the network. */
    data object Broadcasted : TransactionStatus

    /**
     * Wallet has produced a valid signature but is not broadcasting (e.g. PSBT co-signing where the
     * orchestrating dApp assembles and broadcasts the final tx). Distinct from [Broadcasted] so the
     * success screen does not suggest the tx is already on-chain.
     */
    data object Signed : TransactionStatus

    /** Transaction is in the mempool but not yet included in a block. */
    data object Pending : TransactionStatus

    /**
     * Polling reached its time budget without a terminal on-chain result. The transaction may still
     * confirm later (or, for XRP, may have expired past its LastLedgerSequence without moving
     * funds). This is a neutral terminal state — never a hard failure — so the user isn't told the
     * send failed when funds were untouched.
     */
    data object StillConfirming : TransactionStatus

    /** Transaction has been confirmed on-chain. */
    data object Confirmed : TransactionStatus

    /** Transaction failed or was rejected. */
    data class Failed(val cause: UiText) : TransactionStatus

    /**
     * Transaction was accepted by the chain but refunded by the protocol (e.g. THORChain/Maya `type
     * == "refund"` or `type == "failed"` actions where the user's funds are returned).
     *
     * @property reason Human-readable explanation from `metadata.refund.reason` /
     *   `metadata.failed.reason`, e.g. "deposits are paused for asset (ETH.USDT...)".
     */
    data class Refunded(val reason: UiText) : TransactionStatus
}

/**
 * Immutable aggregate of every observable keysign UI value. A single backing [MutableStateFlow] in
 * [KeysignViewModel] is the only place these fields are mutated, giving the keysign state machine
 * one source of truth and keeping mutually-dependent fields (e.g. [txHash]/[txLink]) consistent.
 *
 * @property signingState Current signing/broadcast phase rendered by the keysign screen.
 * @property transactionUiModel Transaction UI model, enriched after address-book lookup and once
 *   the actual EVM fee is known.
 * @property txHash Primary transaction hash after broadcast, or empty before broadcast.
 * @property approveTxHash ERC-20 approval transaction hash, or empty when not applicable.
 * @property txLink Explorer URL for [txHash], or empty before broadcast.
 * @property approveTxLink Explorer URL for [approveTxHash], or empty when not applicable.
 * @property swapProgressLink Deep-link to the swap progress page, or null when not a swap.
 * @property showSaveToAddressBook True when the destination address is not yet saved and is not
 *   another vault.
 */
@Immutable
internal data class KeysignUiState(
    val signingState: KeysignState = KeysignState.CreatingInstance,
    val transactionUiModel: TransactionTypeUiModel? = null,
    val txHash: String = "",
    val approveTxHash: String = "",
    val txLink: String = "",
    val approveTxLink: String = "",
    val swapProgressLink: String? = null,
    val showSaveToAddressBook: Boolean = false,
)

/**
 * One keyType-specific signing attempt: builds and runs the native helper, reporting peer-wait
 * transitions via [onWaitingForPeers]/[onPeersResumed], and returns the produced signatures keyed
 * by message hash.
 */
private typealias RunKeysign =
    suspend (onWaitingForPeers: (List<String>) -> Unit, onPeersResumed: () -> Unit) -> Map<
            String,
            KeysignResponse,
        >

/** ViewModel that drives the keysign screen: starts the TSS signing flow and tracks its state. */
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
    private val awaitApprovalConfirmation: AwaitApprovalConfirmationUseCase,
    private val explorerLinkRepository: ExplorerLinkRepository,
    private val navigator: Navigator<Destination>,
    private val sessionApi: SessionApi,
    private val encryption: Encryption,
    private val featureFlagApi: FeatureFlagApi,
    private val pullTssMessages: PullTssMessagesUseCase,
    private val addressBookRepository: AddressBookRepository,
    private val txStatusConfigurationProvider: TxStatusConfigurationProvider,
    private val txStatusPoller: KeysignTxStatusPoller,
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val transactionHistoryRepository: TransactionHistoryRepository,
    private val balanceRepository: BalanceRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
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

    private val _state =
        MutableStateFlow(KeysignUiState(transactionUiModel = transactionTypeUiModel))

    /** Aggregated read-only keysign UI state; observed by the Compose screen. */
    val state: StateFlow<KeysignUiState> = _state.asStateFlow()

    /** Test-only seam to seed [state] without driving the full signing flow. */
    @VisibleForTesting
    internal fun updateUiStateForTesting(transform: (KeysignUiState) -> KeysignUiState) {
        _state.update(transform)
    }

    /**
     * dApp identity attached to the keysign request, if any. Read by the verify and done banners.
     */
    val dappMetadata: DAppMetadata?
        get() = keysignPayload?.dappMetadata

    /**
     * Logo shown inside the keysign Rive animation ("toToken" image input). Mirrors iOS
     * (`keysignPayload.coin.logo`) and Windows (`getKeysignPayloadLogoSrc`): the signing coin's
     * logo for every keysign — Send, Swap, Deposit — with `tokenLogoRes()` falling back to the
     * chain logo. Sourced from [keysignPayload] (synchronous at construction) rather than the
     * async-loaded [transactionTypeUiModel], which may still be null while signing is in progress.
     */
    @DrawableRes val coinLogoRes: Int? = keysignPayload?.coin?.tokenLogoRes()

    private var tssInstance: ServiceImpl? = null
    private var tssMessenger: TssMessenger? = null
    private val localStateAccessor: LocalStateAccessor = LocalStateAccessor(vault)

    private var pullTssMessagesJob: Job? = null
    private val signatures: MutableMap<String, KeysignResponse> = mutableMapOf()
    private var featureFlags: FeatureFlagJson? = null

    private var pollingTxStatusJob: Job? = null

    private val saveKeysignTransactionHistory =
        SaveKeysignTransactionHistoryUseCase(transactionHistoryRepository)
    private val updateEvmActualFee = UpdateEvmActualFeeUseCase(evmApiFactory, gasFeeToEstimatedFee)
    private val broadcastKeysign =
        BroadcastKeysignUseCase(
            broadcastTx = broadcastTx,
            awaitApprovalConfirmation = awaitApprovalConfirmation,
            explorerLinkRepository = explorerLinkRepository,
            evmApiFactory = evmApiFactory,
            balanceRepository = balanceRepository,
        )

    init {
        // Both Send and Deposit done-screens resolve the same destination labels + "Add to Address
        // Book" affordance, so a Cosmos staking deposit renders identically on the initiator and a
        // joining device (issue #4939).
        val target = transactionTypeUiModel?.addressBookTarget()
        if (target != null) {
            viewModelScope.safeLaunch {
                val labels = resolveDestinationLabels(target.chain, target.dstAddress)
                _state.update {
                    it.copy(
                        showSaveToAddressBook = labels.showSaveToAddressBook,
                        transactionUiModel =
                            transactionTypeUiModel.withResolvedLabels(
                                srcVaultName = vault.name,
                                dstVaultName = labels.dstVaultName,
                                dstAddressBookTitle = labels.dstAddressBookTitle,
                            ),
                    )
                }
            }
        }
    }

    /** Destination labels resolved for the Transaction-complete screen. */
    private data class DestinationLabels(
        val dstVaultName: String?,
        val dstAddressBookTitle: String?,
        val showSaveToAddressBook: Boolean,
    )

    private suspend fun resolveDestinationLabels(
        chain: Chain,
        dstAddress: String,
    ): DestinationLabels {
        val allVaults = withContext(Dispatchers.IO) { vaultRepository.getAll() }
        val dstVaultName =
            resolveDstVaultName(
                allVaults = allVaults,
                chain = chain,
                dstAddress = dstAddress,
                chainAccountAddressRepository = chainAccountAddressRepository,
            )

        val isSavedBefore =
            addressBookRepository.entryExists(address = dstAddress, chainId = chain.id)

        val dstAddressBookTitle =
            if (dstVaultName == null && isSavedBefore) {
                addressBookRepository.getEntry(chain.id, dstAddress)?.title
            } else null

        return DestinationLabels(
            dstVaultName = dstVaultName,
            dstAddressBookTitle = dstAddressBookTitle,
            showSaveToAddressBook = isSavedBefore.not() && dstVaultName == null,
        )
    }

    /** Begins the TSS signing flow for the configured vault and payload. */
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

    /**
     * Runs the DKLS/KeyImport signing path for the active [keyType], dispatching to the matching
     * native helper (ECDSA/EdDSA/MLDSA) via [signWithKeyType] and feeding the result through
     * [runSigningFlow]. The public-key overrides and [chainPath] let per-chain signing keys be used
     * instead of the vault's root keys.
     */
    private suspend fun startKeysignCore(
        ecdsaPublicKeyOverride: String?,
        eddsaPublicKeyOverride: String?,
        chainPath: String,
    ) {
        runSigningFlow(onAllSigned = { extractCustomMessageSignature() }) {
            if (keysignPayload == null && customMessagePayload == null) {
                error("Keysign payload is null")
            }
            when (keyType) {
                TssKeyType.ECDSA ->
                    signWithKeyType(
                        activeState = KeysignState.KeysignECDSA,
                        waitingProgress = 0.33f,
                    ) { onWaitingForPeers, onPeersResumed ->
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
                                onWaitingForPeers = onWaitingForPeers,
                                onPeersResumed = onPeersResumed,
                            )
                        dkls.keysignWithRetry()
                        dkls.signatures
                    }

                TssKeyType.EDDSA ->
                    signWithKeyType(
                        activeState = KeysignState.KeysignEdDSA,
                        waitingProgress = 0.66f,
                    ) { onWaitingForPeers, onPeersResumed ->
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
                                onWaitingForPeers = onWaitingForPeers,
                                onPeersResumed = onPeersResumed,
                            )
                        schnorr.keysignWithRetry()
                        schnorr.signatures
                    }

                TssKeyType.MLDSA -> {
                    Timber.d(
                        "MLDSA keysign: pubKeyMLDSA=%s..., keyshares=%s, isInitiating=%b",
                        vault.pubKeyMLDSA.take(20),
                        vault.keyshares.map { it.pubKey.take(20) },
                        isInitiatingDevice,
                    )
                    signWithKeyType(
                        activeState = KeysignState.KeysignMLDSA,
                        waitingProgress = 0.66f,
                    ) { onWaitingForPeers, onPeersResumed ->
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
                                onWaitingForPeers = onWaitingForPeers,
                                onPeersResumed = onPeersResumed,
                            )
                        mldsa.keysignWithRetry()
                        mldsa.signatures
                    }
                }
            }
        }
    }

    /**
     * Runs one keyType-specific signing attempt and merges its signatures into [signatures],
     * running the empty-signatures check once. Collapses the otherwise-identical ECDSA/EdDSA/MLDSA
     * branches, which differ only by [activeState], [waitingProgress], and which native helper
     * [runKeysign] builds.
     */
    private suspend fun signWithKeyType(
        activeState: KeysignState,
        waitingProgress: Float,
        runKeysign: RunKeysign,
    ) {
        _state.update { it.copy(signingState = activeState) }
        val newSignatures =
            runKeysign(
                { peers ->
                    _state.update {
                        it.copy(signingState = KeysignState.WaitingForPeer(peers, waitingProgress))
                    }
                },
                { _state.update { it.copy(signingState = activeState) } },
            )
        if (newSignatures.isEmpty()) {
            error("Failed to sign transaction, signatures empty")
        }
        this.signatures += newSignatures
    }

    /**
     * Shared post-signing tail reused by the legacy GG20 and DKLS/KeyImport paths: runs [sign],
     * then [onAllSigned], then broadcasts (or finishes without broadcast), handling
     * cancellation/errors uniformly. [cancelPullJobOnFinish] cancels [pullTssMessagesJob] on
     * success and on cancellation — only the legacy GG20 path pulls TSS messages.
     */
    private suspend fun runSigningFlow(
        cancelPullJobOnFinish: Boolean = false,
        onAllSigned: suspend () -> Unit = {},
        sign: suspend () -> Unit,
    ) {
        try {
            sign()
            Timber.d("All messages signed, broadcasting transaction")
            onAllSigned()
            if (!skipBroadcast()) {
                broadcastTransaction()
                checkThorChainTxResult()
            } else {
                finishWithoutBroadcast()
            }
            if (customMessagePayload != null) {
                // For custom message signing, we consider the flow complete after signing without
                // broadcasting
                _state.update {
                    it.copy(
                        signingState = KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
                    )
                }
            }
            if (cancelPullJobOnFinish) pullTssMessagesJob?.cancel()
        } catch (e: CancellationException) {
            if (cancelPullJobOnFinish) pullTssMessagesJob?.cancel()
            throw e
        } catch (e: Exception) {
            Timber.e(e)
            _state.update {
                it.copy(signingState = KeysignState.Error(e.message or R.string.unknown_error))
            }
        }
    }

    /**
     * For custom-message signing, derives the transaction signature from the response stored under
     * the first message-to-sign. No-op when there is no [customMessagePayload].
     */
    private fun extractCustomMessageSignature() {
        if (customMessagePayload == null) return
        require(messagesToSign.isNotEmpty()) {
            "messagesToSign must not be empty when extracting custom message"
        }
        val customMessageKey = messagesToSign.first()
        val customMessageResp =
            signatures[customMessageKey]
                ?: error("No signature found for custom message $customMessageKey")
        calculateCustomMessageSignature(customMessageResp)
    }

    /**
     * Transitions past the keysign spinner without broadcasting. Used by PSBT co-signing and other
     * dApp-orchestrated flows where only the dApp can assemble the final transaction. The `Signed`
     * status (vs. `Broadcasted`) keeps the success screen honest — the tx is not yet on-chain.
     */
    private fun finishWithoutBroadcast() {
        _state.update {
            it.copy(signingState = KeysignState.KeysignFinished(TransactionStatus.Signed))
        }
    }

    private fun skipBroadcast(): Boolean {
        val payload = keysignPayload
        if (payload?.signBitcoin != null) {
            // PSBT co-signing: only the dApp orchestrating the session can assemble
            // the final signed transaction, so the wallet must never broadcast.
            return true
        }
        val flag = payload?.skipBroadcast ?: false
        Timber.d("SkipBroadcastFlag, value: $flag")
        return flag
    }

    private suspend fun signAndBroadcast() {
        Timber.d("Start to SignAndBroadcast")
        _state.update { it.copy(signingState = KeysignState.CreatingInstance) }
        runSigningFlow(
            cancelPullJobOnFinish = true,
            onAllSigned = { extractCustomMessageSignature() },
        ) {
            featureFlags = featureFlagApi.getFeatureFlags()
            val isEncryptionGcm = featureFlags?.isEncryptGcmEnabled == true

            tssMessenger =
                TssMessenger(
                    serverUrl,
                    sessionId,
                    encryptionKeyHex,
                    sessionApi,
                    CoroutineScope(viewModelScope.coroutineContext + Dispatchers.IO),
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
        }
    }

    private suspend fun checkThorChainTxResult() {
        val chainSpecific = keysignPayload?.blockChainSpecific
        if (chainSpecific !is BlockChainSpecific.THORChain) return
        if (!chainSpecific.isDeposit) return
        val transactionDetail = thorChainApi.getTransactionDetail(_state.value.txHash)

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
                viewModelScope.launch(Dispatchers.IO) {
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
                        _state.update { it.copy(signingState = KeysignState.KeysignECDSA) }
                        service.keysignECDSA(keysignReq)
                    }

                    TssKeyType.EDDSA -> {
                        keysignReq.pubKey = vault.pubKeyEDDSA
                        _state.update { it.copy(signingState = KeysignState.KeysignEdDSA) }
                        service.keysignEdDSA(keysignReq)
                    }

                    else -> error("MLDSA is not supported in legacy TSS signing")
                }
            if (keysignResp.r.isNullOrEmpty() || keysignResp.s.isNullOrEmpty()) {
                throw Exception("Failed to sign message")
            }
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
        val signature =
            when (keyType) {
                // EdDSA chains (Solana, TON, etc.) use little-endian reversed r+s
                TssKeyType.EDDSA -> keysignResp.getSignature().toHexString()
                // ECDSA chains use standard r+s+recoveryId (matches iOS/Extension)
                TssKeyType.ECDSA -> keysignResp.getSignatureWithRecoveryID().toHexString()
                // MLDSA keysign populates derSignature rather than r/s/recoveryID
                TssKeyType.MLDSA -> keysignResp.derSignature
            }
        _state.update { it.copy(txHash = signature) }
    }

    private suspend fun broadcastTransaction() {
        val payload = keysignPayload ?: return

        applyBroadcastResult(
            broadcastKeysign(
                vault = vault,
                payload = payload,
                signatures = signatures,
                isInitiatingDevice = isInitiatingDevice,
            )
        )
    }

    internal suspend fun applyBroadcastResult(result: KeysignBroadcastResult) {
        when (result) {
            is KeysignBroadcastResult.ApprovalNotConfirmed -> {
                _state.update {
                    it.copy(
                        approveTxHash = result.approveTxHash,
                        approveTxLink = result.approveTxLink,
                        signingState =
                            if (result.timedOut) {
                                KeysignState.Error(R.string.swap_error_approval_timeout.asUiText())
                            } else {
                                // Approval reverted on-chain — a terminal on-chain failure, not a
                                // TSS/keysign failure. Land on the swap overview with a Failed
                                // status (approval tx hash/link set above) instead of the generic
                                // "Signing Error / try again" screen, which would wrongly imply a
                                // pairing/network problem and drop the explorer link.
                                KeysignState.KeysignFinished(
                                    TransactionStatus.Failed(
                                        R.string.swap_error_approval_failed.asUiText()
                                    )
                                )
                            },
                    )
                }
            }
            is KeysignBroadcastResult.Broadcasted -> {
                _state.update {
                    it.copy(
                        approveTxHash = result.approveTxHash,
                        approveTxLink = result.approveTxLink,
                    )
                }
                val txHash = result.txHash
                if (txHash != null) {
                    _state.update {
                        it.copy(
                            txHash = txHash,
                            txLink = result.txLink,
                            swapProgressLink = result.swapProgressLink,
                        )
                    }
                    saveTransactionHistory(txHash, result.chain)
                }
                // A Solana dApp batch broadcasts several transactions; the extra ones are
                // persisted to history independently of the primary hash — each with its own
                // explorer link, not the primary tx's — while [txHash] stays the primary hash
                // driving the done screen and status polling.
                result.additionalTxHashes.forEach { hash ->
                    saveTransactionHistory(
                        txHash = hash,
                        chain = result.chain,
                        explorerUrl = explorerLinkRepository.getTransactionLink(result.chain, hash),
                    )
                }
                if (txHash != null && txStatusConfigurationProvider.supportTxStatus(result.chain)) {
                    startForegroundPolling(txHash, result.chain)
                } else {
                    // Either the chain has no status polling or the broadcast produced no
                    // primary hash. Land on the terminal "broadcasted" state instead of leaving
                    // signingState at the last signing state forever (infinite spinner → the
                    // user may force-retry and double-send).
                    _state.update {
                        it.copy(
                            signingState =
                                KeysignState.KeysignFinished(TransactionStatus.Broadcasted)
                        )
                    }
                }
            }
        }
    }

    /**
     * [explorerUrl] overrides the state-derived link for the extra transactions of a Solana dApp
     * batch, whose ViewModel state only carries the primary tx's link.
     */
    internal suspend fun saveTransactionHistory(
        txHash: String,
        chain: Chain,
        explorerUrl: String? = null,
    ) {
        saveKeysignTransactionHistory(
            vaultId = vault.id,
            txHash = txHash,
            chain = chain,
            explorerUrl = explorerUrl ?: _state.value.let { it.swapProgressLink ?: it.txLink },
            transactionHistoryData = transactionHistoryData,
            // Polkadot extrinsics are mortal: persist the head block at broadcast so the status
            // poller can scan the absolute inclusion window instead of a head-relative one that
            // drifts out of reach. Null for other chains.
            broadcastBlockNumber =
                (keysignPayload?.blockChainSpecific as? BlockChainSpecific.Polkadot)
                    ?.currentBlockNumber
                    ?.toLong(),
        )
    }

    /**
     * Starts foreground transaction-status polling, delegating the status-service / SwapKit polling
     * strategies to [txStatusPoller]. Mirrors each observed status into [state] and, once a
     * terminal status is reached, replaces the estimated EVM fee with the actual burned fee.
     */
    private fun startForegroundPolling(txHash: String, chain: Chain) {
        pollingTxStatusJob?.cancel()
        pollingTxStatusJob =
            viewModelScope.safeLaunch {
                val terminal =
                    txStatusPoller.poll(txHash, chain, isSwapKitSwap = isSwapKitSwap()) { result ->
                        _state.update {
                            it.copy(
                                signingState =
                                    KeysignState.KeysignFinished(
                                        transactionStatus = result.toTransactionStatus()
                                    )
                            )
                        }
                    }
                if (terminal != null) tryUpdateEvmActualFee(txHash, chain)
            }
    }

    /** True when this keysign is a SwapKit-routed swap, which settles on its destination leg. */
    private fun isSwapKitSwap(): Boolean =
        (transactionHistoryData as? SwapTransactionHistoryData)?.provider ==
            SwapProvider.SWAPKIT.getSwapProviderId()

    /**
     * After confirmation, fetches the receipt and replaces the estimated fee with the actual burned
     * fee (`gasUsed × effectiveGasPrice`). Falls back silently to the estimate on any error.
     */
    internal fun tryUpdateEvmActualFee(txHash: String, chain: Chain) {
        if (chain.standard != TokenStandard.EVM) return
        val coin = keysignPayload?.coin ?: return

        viewModelScope.safeLaunch(
            onError = { e -> Timber.w(e, "Failed to update EVM actual fee for %s", txHash) }
        ) {
            val estimatedFee = updateEvmActualFee(txHash, chain, coin) ?: return@safeLaunch
            _state.update { current ->
                val sendTx =
                    current.transactionUiModel as? TransactionTypeUiModel.Send
                        ?: return@update current
                current.copy(
                    transactionUiModel =
                        TransactionTypeUiModel.Send(
                            sendTx.tx.copy(
                                networkFeeTokenValue = estimatedFee.formattedTokenValue,
                                networkFeeFiatValue = estimatedFee.formattedFiatValue,
                            )
                        )
                )
            }
        }
    }

    /** Cancels the transaction-status polling job and cleans up the background service. */
    fun stopPolling() {
        pollingTxStatusJob?.cancel()
        txStatusPoller.stopPolling()
    }

    /**
     * Navigates to home once signing reached the terminal [KeysignState.KeysignFinished] state,
     * otherwise navigates back. Derived from [state] rather than an imperative flag.
     */
    fun navigateToHome() {
        viewModelScope.launch {
            if (_state.value.signingState is KeysignState.KeysignFinished) {
                navigator.route(Route.Home(), NavigationOptions(clearBackStack = true))
            } else {
                navigator.navigate(Destination.Back)
            }
        }
    }

    /** Opens the address-book entry form pre-populated with the send/deposit destination. */
    fun navigateToAddressBook() {
        val target = transactionTypeUiModel?.addressBookTarget() ?: return
        viewModelScope.launch {
            navigator.route(
                Route.AddressEntry(
                    vaultId = vault.id,
                    address = target.dstAddress,
                    chainId = target.chain.id,
                )
            )
        }
    }

    /** Maps a polled [TransactionResult] to the [TransactionStatus] shown on the done screen. */
    private fun TransactionResult.toTransactionStatus() =
        when (this) {
            TransactionResult.Confirmed -> TransactionStatus.Confirmed
            is TransactionResult.Failed -> TransactionStatus.Failed(this.reason.asUiText())
            is TransactionResult.Refunded -> TransactionStatus.Refunded(this.reason.asUiText())
            TransactionResult.TimedOut -> TransactionStatus.StillConfirming
            TransactionResult.NotFound,
            TransactionResult.Pending -> TransactionStatus.Pending
        }

    /** Stops polling and cleans up resources when the ViewModel is destroyed. */
    override fun onCleared() {
        stopPolling()
        txStatusPoller.cleanup()
        super.onCleared()
    }
}

/** The (chain, destination) pair whose address-book labels the done screen resolves. */
private data class AddressBookTarget(val chain: Chain, val dstAddress: String)

/**
 * The destination to resolve labels for on the Transaction-complete screen — sends and deposits
 * both have one; sign-message and other types do not. A blank destination (some deposits carry
 * none) yields `null` so no lookup runs.
 */
private fun TransactionTypeUiModel.addressBookTarget(): AddressBookTarget? {
    val (chain, dstAddress) =
        when (this) {
            is TransactionTypeUiModel.Send -> tx.token.token.chain to tx.dstAddress
            is TransactionTypeUiModel.Deposit ->
                depositTransactionUiModel.token.token.chain to depositTransactionUiModel.dstAddress
            else -> return null
        }
    return if (dstAddress.isBlank()) null else AddressBookTarget(chain, dstAddress)
}

/** Returns a copy of this model with the resolved From/To labels applied (Send or Deposit only). */
private fun TransactionTypeUiModel.withResolvedLabels(
    srcVaultName: String,
    dstVaultName: String?,
    dstAddressBookTitle: String?,
): TransactionTypeUiModel =
    when (this) {
        is TransactionTypeUiModel.Send ->
            TransactionTypeUiModel.Send(
                tx.copy(
                    srcVaultName = srcVaultName,
                    dstVaultName = dstVaultName,
                    dstAddressBookTitle = dstAddressBookTitle,
                )
            )
        is TransactionTypeUiModel.Deposit ->
            TransactionTypeUiModel.Deposit(
                depositTransactionUiModel.copy(
                    srcVaultName = srcVaultName,
                    dstVaultName = dstVaultName,
                    dstAddressBookTitle = dstAddressBookTitle,
                )
            )
        else -> this
    }
