package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.IoDispatcher
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.TransactionId
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ContractAbiRepository
import com.vultisig.wallet.data.repositories.FourByteRepository
import com.vultisig.wallet.data.repositories.PrettyJson
import com.vultisig.wallet.data.repositories.TokenMetadataResolver
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.repositories.VaultPasswordRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.securityscanner.BLOCKAID_PROVIDER
import com.vultisig.wallet.data.securityscanner.SecurityScannerContract
import com.vultisig.wallet.data.securityscanner.SecurityScannerResult
import com.vultisig.wallet.data.securityscanner.isChainSupported
import com.vultisig.wallet.data.usecases.IsVaultHasFastSignByIdUseCase
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.components.hero.HeroContent
import com.vultisig.wallet.ui.models.keysign.DecodedFunctionParam
import com.vultisig.wallet.ui.models.keysign.FunctionInfo
import com.vultisig.wallet.ui.models.keysign.KeysignInitType
import com.vultisig.wallet.ui.models.keysign.TonMessageUiModel
import com.vultisig.wallet.ui.models.keysign.approvalSpenderArgIndex
import com.vultisig.wallet.ui.models.keysign.enrichDecodedCall
import com.vultisig.wallet.ui.models.keysign.isUnlimitedApproval
import com.vultisig.wallet.ui.models.keysign.prettifyEvmFunctionName
import com.vultisig.wallet.ui.models.mappers.TransactionToUiModelMapper
import com.vultisig.wallet.ui.models.swap.ValuedToken
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.navigation.util.LaunchKeysignUseCase
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.handleSigningFlowCommon
import com.vultisig.wallet.ui.utils.normalizeAddressForLookup
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@Immutable
internal data class TransactionDetailsUiModel(
    val token: ValuedToken = ValuedToken.Empty,
    val networkFeeFiatValue: String = "",
    val networkFeeTokenValue: String = "",
    val srcAddress: String = "",
    val srcVaultName: String? = null,
    val dstAddress: String = "",
    val dstVaultName: String? = null,
    val dstAddressBookTitle: String? = null,
    val dstLabel: String? = null,
    val memo: String? = null,
    val signAmino: String? = null,
    val signDirect: String? = null,
    val signSolana: String? = null,
    /**
     * Per-message rows for a TonConnect signing request, each decoded from its BOC body into an
     * operation label, real recipient, forward amount, and the raw payload. Empty for non-TON or
     * undecodable requests. Built in [com.vultisig.wallet.ui.models.keysign.mapTonMessages].
     */
    val tonMessages: List<TonMessageUiModel> = emptyList(),
    val functionSignature: String? = null,
    val functionInputs: String? = null,
    val functionName: String? = null,
    /**
     * True when the transaction is an ERC-20 approval call with an effectively unlimited amount.
     */
    val isUnlimitedApproval: Boolean = false,
    /** The address being granted the unlimited allowance (args[0] of the approval call). */
    val approvalSpender: String? = null,
    /**
     * Resolved ERC-20 ticker for the token contract being approved (overrides native coin ticker).
     * For tokens the active vault doesn't hold, this falls through to an on-chain `symbol()` call
     * via [com.vultisig.wallet.data.repositories.TokenMetadataResolver] so unknown stablecoins and
     * LP tokens still surface a ticker in the warning row.
     */
    val approvalTokenTicker: String? = null,
    /**
     * Friendly label for the destination contract when it is on the
     * [com.vultisig.wallet.data.repositories.KnownEvmContracts] allowlist (Uniswap routers,
     * Permit2, etc.). Displayed alongside the contract address so the `To` row reads as `Uniswap V3
     * Router (0x68b3…fC45)` instead of a bare hash.
     */
    val dstContractLabel: String? = null,
    /**
     * Per-parameter labelled rows rendered inside the expandable Transaction Details section. For
     * known function shapes the rows carry semantic labels (`Spender`, `Recipient`, `Amount`);
     * unknown signatures fall back to positional `#N (type)` rows. Null when the decoder couldn't
     * interpret the call — in that case the screen hides the rich rows and the raw signature line
     * still serves as the disclosure.
     */
    val decodedFunctionParams: List<DecodedFunctionParam>? = null,
    /**
     * True when the call decoded to an aggregate Uniswap Universal Router swap. The verify screen
     * uses this to swap the toolbar title from "Send overview" to "Swap overview" and the card
     * title from the raw `Execute` function name to "Swap" so the user sees real intent, not the
     * router's `execute(...)` plumbing.
     */
    val isUniversalRouterSwap: Boolean = false,
    /**
     * Resolved hero content for the dApp signing screens. Populated by [BuildHeroContentUseCase]
     * once the Blockaid simulation completes. When non-null, screens render this in place of the
     * function-name title or the native-amount `VsOverviewToken`. When null, screens fall back to
     * the existing display logic (function-name title for EVM contract calls, otherwise native
     * amount).
     */
    val heroContent: HeroContent? = null,
)

@Immutable
internal data class VerifyTransactionUiModel(
    val transaction: TransactionDetailsUiModel = TransactionDetailsUiModel(),
    val consentAddress: Boolean = false,
    val consentAmount: Boolean = false,
    val errorText: UiText? = null,
    val hasFastSign: Boolean = false,
    val txScanStatus: TransactionScanStatus = TransactionScanStatus.NotStarted,
    val showScanningWarning: Boolean = false,
    val isLoadingFees: Boolean = false,
) {
    val hasAllConsents: Boolean
        get() = consentAddress && consentAmount
}

/**
 * Annotated [Immutable] so the Compose compiler treats every variant as stable for skipping. The
 * inner [SecurityScannerResult] holds a `List<SecurityWarning>` which Compose infers as unstable,
 * but in practice the list is never mutated post-construction; the annotation closes that gap so
 * `VerifyTransactionUiModel`'s own `@Immutable` declaration is not silently downgraded.
 */
@Immutable
sealed class TransactionScanStatus {
    data object NotStarted : TransactionScanStatus()

    data object Scanning : TransactionScanStatus()

    data class Scanned(val result: SecurityScannerResult) : TransactionScanStatus()

    data class Error(val message: String, val provider: String) : TransactionScanStatus()
}

@HiltViewModel
internal class VerifyTransactionViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTransactionToUiModel: TransactionToUiModelMapper,
    private val transactionRepository: TransactionRepository,
    private val vaultPasswordRepository: VaultPasswordRepository,
    private val launchKeysign: LaunchKeysignUseCase,
    private val isVaultHasFastSignById: IsVaultHasFastSignByIdUseCase,
    private val securityScannerService: SecurityScannerContract,
    private val vaultRepository: VaultRepository,
    private val addressBookRepository: AddressBookRepository,
    private val fourByteRepository: FourByteRepository,
    private val tokenMetadataResolver: TokenMetadataResolver,
    private val contractAbiRepository: ContractAbiRepository,
    private val tokenRepository: TokenRepository,
    @param:PrettyJson private val json: Json,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.VerifySend>()

    private val transactionId: TransactionId = args.transactionId
    private val vaultId: String = args.vaultId

    private var transaction: Transaction? = null

    private val _uiState = MutableStateFlow(VerifyTransactionUiModel())
    val uiState: StateFlow<VerifyTransactionUiModel> = _uiState.asStateFlow()
    private val password = MutableStateFlow<String?>(null)

    private val _fastSignFlow = Channel<Boolean>()
    val fastSignFlow = _fastSignFlow.receiveAsFlow()

    private var _fastSign = false

    init {
        loadFastSign()
        loadTransaction()
        loadPassword()
    }

    fun checkConsentAddress(checked: Boolean) {
        viewModelScope.launch { _uiState.update { it.copy(consentAddress = checked) } }
    }

    fun checkConsentAmount(checked: Boolean) {
        viewModelScope.launch { _uiState.update { it.copy(consentAmount = checked) } }
    }

    fun authFastSign() {
        keysign(KeysignInitType.BIOMETRY)
    }

    private fun tryToFastSignWithPassword(): Boolean {
        if (password.value != null) {
            return false
        } else {
            keysign(KeysignInitType.PASSWORD)
            return true
        }
    }

    fun joinKeySign() {
        _fastSign = false
        handleSigningFlowCommon(
            txScanStatus = _uiState.value.txScanStatus,
            showWarning = { _uiState.update { it.copy(showScanningWarning = true) } },
            onSign = { keysign(KeysignInitType.QR_CODE) },
        )
    }

    private fun joinKeySignAndSkipWarnings() {
        _uiState.update { it.copy(showScanningWarning = false) }
        keysign(KeysignInitType.QR_CODE)
    }

    fun fastSign() {
        _fastSign = true
        handleSigningFlowCommon(
            txScanStatus = _uiState.value.txScanStatus,
            showWarning = { _uiState.update { it.copy(showScanningWarning = true) } },
            onSign = { fastSignAndSkipWarnings() },
        )
    }

    private fun fastSignAndSkipWarnings() {
        _uiState.update { it.copy(showScanningWarning = false) }

        if (!tryToFastSignWithPassword()) {
            viewModelScope.launch { _fastSignFlow.send(true) }
        }
    }

    fun onConfirmScanning() {
        if (!_fastSign) {
            joinKeySignAndSkipWarnings()
        } else {
            fastSignAndSkipWarnings()
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorText = null) }
    }

    fun dismissScanningWarning() {
        _uiState.update { it.copy(showScanningWarning = false) }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    private fun keysign(keysignInitType: KeysignInitType) {
        if (_uiState.value.hasAllConsents) {
            viewModelScope.launch {
                launchKeysign(
                    keysignInitType,
                    transactionId,
                    password.value,
                    Route.Keysign.Keysign.TxType.Send,
                    vaultId,
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    errorText =
                        UiText.StringResource(R.string.verify_transaction_error_not_enough_consent)
                )
            }
        }
    }

    private fun loadPassword() {
        viewModelScope.launch { password.value = vaultPasswordRepository.getPassword(vaultId) }
    }

    private fun loadFastSign() {
        viewModelScope.launch {
            val hasFastSign = isVaultHasFastSignById(vaultId)
            _uiState.update { it.copy(hasFastSign = hasFastSign) }
        }
    }

    private fun loadTransaction() {
        viewModelScope.safeLaunch(
            onError = {
                Timber.e(it, "Failed to load transaction")
                navigator.back()
            }
        ) {
            val tx =
                transactionRepository.getTransaction(transactionId)
                    ?: error("Transaction not found: $transactionId")
            transaction = tx
            val transactionUiModel = mapTransactionToUiModel(tx)

            val allVaults = withContext(ioDispatcher) { vaultRepository.getAll() }
            val chain = tx.token.chain
            val srcVaultName = allVaults.find { it.id == vaultId }?.name
            val normalizedDstAddress = normalizeAddressForLookup(tx.dstAddress)
            val dstVaultName =
                allVaults
                    .firstOrNull { vault ->
                        vault.coins.any {
                            it.chain == chain &&
                                normalizeAddressForLookup(it.address) == normalizedDstAddress
                        }
                    }
                    ?.name
            val dstInAddressBook =
                dstVaultName == null && addressBookRepository.entryExists(chain.id, tx.dstAddress)
            val dstAddressBookTitle =
                if (dstInAddressBook) {
                    runCatching { addressBookRepository.getEntry(chain.id, tx.dstAddress).title }
                        .getOrNull()
                } else null

            val memo = tx.memo
            val functionInfo =
                if (chain.standard == TokenStandard.EVM && !memo.isNullOrEmpty()) {
                    withContext(ioDispatcher) {
                        val sig = fourByteRepository.decodeFunction(memo) ?: return@withContext null
                        FunctionInfo(
                            signature = sig,
                            inputs = fourByteRepository.decodeFunctionArgs(sig, memo),
                            functionName = prettifyEvmFunctionName(sig),
                        )
                    }
                } else null
            val isUnlimitedApproval =
                functionInfo != null &&
                    isUnlimitedApproval(functionInfo.signature, functionInfo.inputs, json)
            val approvalSpender =
                if (isUnlimitedApproval) {
                    val spenderIdx = approvalSpenderArgIndex(functionInfo?.signature ?: "")
                    if (spenderIdx != null) {
                        runCatching {
                                json
                                    .parseToJsonElement(functionInfo?.inputs ?: "[]")
                                    .jsonArray
                                    .getOrNull(spenderIdx)
                                    ?.jsonPrimitive
                                    ?.content
                                    ?.trim()
                                    ?.takeIf { it.isNotEmpty() }
                            }
                            .onFailure { if (it is CancellationException) throw it }
                            .getOrNull()
                    } else null
                } else null
            val decodedExtras =
                enrichDecodedCall(
                    chain = chain,
                    dstAddress = tx.dstAddress,
                    functionInfo = functionInfo,
                    allVaults = allVaults,
                    isUnlimitedApproval = isUnlimitedApproval,
                    json = json,
                    tokenMetadataResolver = tokenMetadataResolver,
                    nativeTokenLookup = { c -> nativeTokenOrNull(c.id) },
                    resolveAbiParams = { c, address, sig ->
                        contractAbiRepository.resolveParams(c, address, sig)
                    },
                )

            val namedUiModel =
                transactionUiModel.copy(
                    srcVaultName = srcVaultName,
                    dstVaultName = dstVaultName,
                    dstAddressBookTitle = dstAddressBookTitle,
                    functionSignature = functionInfo?.signature,
                    functionInputs = functionInfo?.inputs,
                    functionName = functionInfo?.functionName,
                    isUnlimitedApproval = isUnlimitedApproval,
                    approvalSpender = approvalSpender,
                    approvalTokenTicker = decodedExtras.approvalTokenTicker,
                    dstContractLabel = decodedExtras.dstContractLabel,
                    decodedFunctionParams = decodedExtras.decodedFunctionParams,
                    isUniversalRouterSwap = decodedExtras.isUniversalRouterSwap,
                )

            _uiState.update { it.copy(transaction = namedUiModel) }

            scanTransaction()
        }
    }

    private suspend fun scanTransaction() {
        try {
            val tx = transaction ?: return
            val chain = tx.token.chain

            val isSupported =
                securityScannerService.getSupportedChainsByFeature().isChainSupported(chain) &&
                    securityScannerService.isSecurityServiceEnabled()

            if (!isSupported) return

            _uiState.update { it.copy(txScanStatus = TransactionScanStatus.Scanning) }

            val securityScannerTransaction =
                securityScannerService.createSecurityScannerTransaction(tx)

            val result =
                withContext(ioDispatcher) {
                    securityScannerService.scanTransaction(securityScannerTransaction)
                }

            _uiState.update { it.copy(txScanStatus = TransactionScanStatus.Scanned(result)) }
        } catch (t: Throwable) {
            if (t is kotlinx.coroutines.CancellationException) throw t
            val errorMessage = "Security scan failed ${t.message}"
            Timber.e(t, errorMessage)

            _uiState.update {
                val message = t.message ?: errorMessage
                it.copy(
                    txScanStatus =
                        TransactionScanStatus.Error(message = message, provider = BLOCKAID_PROVIDER)
                )
            }
        }
    }

    /**
     * Fetches the chain's native coin for the Universal Router swap-intent decoder so a native-ETH
     * leg renders the right ticker. Non-fatal — a failed RPC just means the row displays the bare
     * zero address. [CancellationException] propagates so structured-concurrency cancellation isn't
     * swallowed.
     */
    private suspend fun nativeTokenOrNull(chainId: String) =
        try {
            tokenRepository.getNativeToken(chainId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Failed to resolve native token for %s", chainId)
            null
        }
}
