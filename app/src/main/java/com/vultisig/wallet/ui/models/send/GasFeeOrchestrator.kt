package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R.string
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.AdvanceGasUiRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.submit.BitcoinPlanService
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asAddressInput
import com.vultisig.wallet.ui.utils.textAsFlow
import java.math.BigInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.proto.Bitcoin

internal class GasFeeOrchestrator(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<SendFormUiModel>,
    private val selectedToken: StateFlow<Coin?>,
    private val accounts: StateFlow<List<Account>>,
    private val gasFee: MutableStateFlow<TokenValue?>,
    private val gasSettings: StateFlow<GasSettings?>,
    private val specific: MutableStateFlow<BlockChainSpecificAndUtxo?>,
    private val planFee: MutableStateFlow<Long?>,
    private val planBtc: MutableStateFlow<Bitcoin.TransactionPlan?>,
    private val addressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val memoFieldState: TextFieldState,
    private val vaultProvider: () -> Vault?,
    private val vaultIdProvider: () -> VaultId?,
    private val accountProvider: () -> Account?,
    private val resolvedDstAddressProvider: () -> String?,
    private val isMaxAmountFlow: StateFlow<Boolean>,
    private val feeServiceComposite: FeeServiceComposite,
    private val tokenRepository: TokenRepository,
    private val addressParserRepository: AddressParserRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val advanceGasUiRepository: AdvanceGasUiRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val bitcoinPlanService: BitcoinPlanService,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
) {
    private val recalculateGasFee = MutableStateFlow(0L)

    fun start() {
        collectGasTokenBalance()
        collectGasFees()
        collectPlanFee()
        collectMaxAmount()
        collectEstimatedFee()
        collectSpecific()
    }

    fun refresh() {
        scope.launch {
            uiState.update { it.copy(isRefreshing = true) }
            recalculateGasFee.update { it + 1 }
            // Rapid toggling of isRefreshing can cause the initial true value to be skipped,
            // displaying only the false value in the UI resulting in the swipe refresh being
            // frozen. This delay prevents missing the true value.
            delay(100)
            uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private fun collectGasTokenBalance() {
        scope.launch {
            selectedToken
                .filterNotNull()
                .map {
                    if (it.isNativeToken) {
                        null
                    } else {
                        accounts.value
                            .find { account ->
                                account.token.isNativeToken && account.token.chain == it.chain
                            }
                            ?.tokenValue
                    }
                }
                .collect { gasTokenBalance ->
                    if (gasTokenBalance == null) {
                        uiState.update { it.copy(gasTokenBalance = null) }
                    } else {
                        uiState.update {
                            it.copy(
                                gasTokenBalance =
                                    UiText.DynamicString(mapTokenValueToString(gasTokenBalance))
                            )
                        }
                    }
                }
        }
    }

    @OptIn(FlowPreview::class)
    private fun collectGasFees() {
        scope.launch {
            combine(
                    combine(
                            selectedToken.filterNotNull(),
                            addressFieldState.textAsFlow(),
                            memoFieldState.textAsFlow(),
                            tokenAmountFieldState.textAsFlow(),
                            recalculateGasFee,
                        ) { token, dst, memo, tokenAmount, nonce ->
                            GasFeeInput(
                                token,
                                dst.asAddressInput(),
                                memo.toString(),
                                tokenAmount,
                                nonce,
                            )
                        }
                        .debounce(350)
                        .distinctUntilChanged()
                        .mapNotNull { (token, dst, memo, tokenAmount) ->
                            val vault = vaultProvider() ?: return@mapNotNull null
                            val tokenAmount = tokenAmount.toString().toBigDecimalOrNull()

                            val tokenAmountInt =
                                tokenAmount?.movePointRight(token.decimal)?.toBigInteger()
                                    ?: return@mapNotNull null

                            val chain = token.chain
                            val blockchainTransaction =
                                Transfer(
                                    coin = token,
                                    vault =
                                        VaultData(
                                            vaultHexChainCode = vault.hexChainCode,
                                            vaultHexPublicKey = vault.getPubKeyByChain(chain),
                                        ),
                                    amount = tokenAmountInt,
                                    to = resolvedDstAddressProvider() ?: dst,
                                    memo = memo,
                                    isMax = false,
                                )

                            val fees =
                                withContext(Dispatchers.IO) {
                                    feeServiceComposite.calculateFees(blockchainTransaction)
                                }
                            val nativeCoin =
                                withContext(Dispatchers.IO) {
                                    tokenRepository.getNativeToken(chain.id)
                                }

                            TokenValue(value = fees.amount, token = nativeCoin)
                        }
                        .catch { Timber.e(it) },
                    gasSettings,
                    specific,
                ) { gasFeeValue, gasSettings, specific ->
                    gasFee.value = adjustGasFee(gasFeeValue, gasSettings, specific)
                }
                .collect()
        }
    }

    private fun collectPlanFee() {
        scope.launch {
            combine(
                    selectedToken.filterNotNull(),
                    addressFieldState.textAsFlow(),
                    tokenAmountFieldState.textAsFlow(),
                    specific.filterNotNull(),
                    memoFieldState.textAsFlow(),
                ) { token, dstAddress, tokenAmount, specific, memo ->
                    PlanFeeInput(token, dstAddress, tokenAmount, specific, memo)
                }
                .combine(recalculateGasFee) { data, _ -> data }
                .mapNotNull { (token, dstAddress, tokenAmount, specific, memo) ->
                    try {
                        val chain = token.chain
                        if (chain.standard != TokenStandard.UTXO || chain == Chain.Cardano) {
                            planFee.value = 1
                            return@mapNotNull null
                        }

                        val vaultId =
                            vaultIdProvider()
                                ?: throw InvalidTransactionDataException(
                                    UiText.StringResource(string.send_error_no_token)
                                )

                        val resolvedDstAddress =
                            addressParserRepository.resolveName(dstAddress.asAddressInput(), chain)
                        val tokenAmountInt =
                            tokenAmount
                                .toString()
                                .toBigDecimal()
                                .movePointRight(token.decimal)
                                .toBigInteger()

                        val plan =
                            bitcoinPlanService.getPlan(
                                vaultId,
                                token,
                                resolvedDstAddress,
                                tokenAmountInt,
                                specific,
                                memo.toString(),
                            )

                        planFee.value = plan.fee
                        planBtc.value = plan
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
                .collect()
        }
    }

    private fun collectMaxAmount() {
        scope.launch {
            isMaxAmountFlow.collect { isMax ->
                val chain = accountProvider()?.token?.chain ?: return@collect
                // Only require to re-trigger utxo chains, due to no change output utxo and
                // therefore less fees
                if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                    val spec =
                        specific.value?.blockChainSpecific as? BlockChainSpecific.UTXO
                            ?: return@collect
                    val updatedSpec =
                        specific.value?.copy(blockChainSpecific = spec.copy(sendMaxAmount = isMax))
                    specific.value = updatedSpec
                }
            }
        }
    }

    private fun collectEstimatedFee() {
        scope.launch {
            combine(
                    selectedToken.filterNotNull(),
                    gasFee.filterNotNull(),
                    gasSettings,
                    planFee.filterNotNull(),
                ) { token, gasFee, gasSettings, planFee ->
                    val chain = token.chain
                    val evmGasSettings = gasSettings as? GasSettings.Eth
                    val estimatedFee =
                        gasFeeToEstimatedFee(
                            GasFeeParams(
                                gasLimit =
                                    if (evmGasSettings != null) evmGasSettings.gasLimit
                                    else BigInteger.valueOf(1),
                                gasFee =
                                    selectGasFeeForFeeEstimation(
                                        chain = chain,
                                        gasFee = gasFee,
                                        planFee = planFee,
                                        evmGasSettings = evmGasSettings,
                                    ),
                                selectedToken = token,
                                perUnit = true,
                            )
                        )

                    uiState.update {
                        it.copy(
                            estimatedFee = UiText.DynamicString(estimatedFee.formattedFiatValue),
                            totalGas = UiText.DynamicString(estimatedFee.formattedTokenValue),
                        )
                    }
                }
                .collect()
        }
    }

    @OptIn(FlowPreview::class)
    private fun collectSpecific() {
        scope.launch {
            // dstAddress is forwarded to getSpecific() for every chain so that
            // TRON fee estimation can account for bandwidth delegated to the receiver.
            // Recomputing specifics on every keystroke would be wasteful, so the
            // address is debounced before triggering the recalculation.
            val dstAddressFlow =
                addressFieldState
                    .textAsFlow()
                    .map { it.toString().asAddressInput() }
                    .debounce(300)
                    .distinctUntilChanged()

            combine(selectedToken.filterNotNull(), gasFee.filterNotNull(), dstAddressFlow) {
                    token,
                    gasFeeValue,
                    dstAddress ->
                    val chain = token.chain
                    val srcAddress = token.address
                    advanceGasUiRepository.updateTokenStandard(token.chain.standard)

                    val validDstAddress =
                        dstAddress.takeIf {
                            it.isNotBlank() && chainAccountAddressRepository.isValid(chain, it)
                        }

                    try {
                        val spec =
                            blockChainSpecificRepository.getSpecific(
                                chain,
                                srcAddress,
                                token,
                                gasFeeValue,
                                isSwap = false,
                                isMaxAmountEnabled = false,
                                isDeposit = false,
                                dstAddress = validDstAddress,
                            )
                        specific.value = spec
                        advanceGasUiRepository.updateBlockChainSpecific(spec.blockChainSpecific)
                        advanceGasUiRepository.showIcon()
                        uiState.update { it.copy(specific = spec) }
                    } catch (e: Exception) {
                        Timber.e(e)
                    }
                }
                .collect()
        }
    }
}

internal fun adjustGasFee(
    gasFee: TokenValue,
    gasSettings: GasSettings?,
    spec: BlockChainSpecificAndUtxo?,
) =
    gasFee.copy(
        value =
            if (
                gasSettings is GasSettings.UTXO &&
                    spec?.blockChainSpecific is BlockChainSpecific.UTXO
            ) {
                gasSettings.byteFee
            } else gasFee.value
    )

private data class GasFeeInput(
    val token: Coin,
    val dst: String,
    val memo: String,
    val tokenAmount: CharSequence,
    val nonce: Long = 0,
)

private data class PlanFeeInput(
    val token: Coin,
    val dstAddress: CharSequence,
    val tokenAmount: CharSequence,
    val specific: BlockChainSpecificAndUtxo,
    val memo: CharSequence,
)
