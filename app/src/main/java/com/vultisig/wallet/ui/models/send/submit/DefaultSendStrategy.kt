package com.vultisig.wallet.ui.models.send.submit

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.blockchain.cosmos.TerraClassicTax
import com.vultisig.wallet.data.blockchain.tron.TRON_STAKING_MEMO_REGEX
import com.vultisig.wallet.data.chains.helpers.RippleDestinationTag
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Transaction
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TransactionRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.models.send.AddressManager
import com.vultisig.wallet.ui.models.send.AmountManager
import com.vultisig.wallet.ui.models.send.ChainValidationService
import com.vultisig.wallet.ui.models.send.GasSettings
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.models.send.SendFocusField
import com.vultisig.wallet.ui.models.send.SendSections
import com.vultisig.wallet.ui.models.send.selectGasFeeForFeeEstimation
import com.vultisig.wallet.ui.models.send.toPlainBigDecimalOrNull
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asAddressInput
import com.vultisig.wallet.ui.utils.asUiText
import java.math.BigDecimal
import java.math.BigInteger
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import wallet.core.jni.proto.Bitcoin

internal class DefaultSendStrategy(
    private val scope: CoroutineScope,
    private val addressFieldState: TextFieldState,
    private val tokenAmountFieldState: TextFieldState,
    private val fiatAmountFieldState: TextFieldState,
    private val memoFieldState: TextFieldState,
    private val destinationTagFieldState: TextFieldState,
    private val accountValidator: AccountValidator,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val transactionRepository: TransactionRepository,
    private val bitcoinPlanService: BitcoinPlanService,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val chainValidationService: ChainValidationService,
    private val addressManager: AddressManager,
    private val amountManager: AmountManager,
    private val gasSettings: StateFlow<GasSettings?>,
    private val planBtc: MutableStateFlow<Bitcoin.TransactionPlan?>,
    private val planFee: MutableStateFlow<Long?>,
    private val accounts: StateFlow<List<Account>>,
    private val appCurrency: StateFlow<AppCurrency>,
    private val vaultIdProvider: () -> String?,
    private val selectedAccountProvider: () -> Account?,
    private val defiTypeProvider: () -> DeFiNavActions?,
    private val currentTronFrozenBalanceProvider: () -> BigDecimal?,
    private val navigator: Navigator<Destination>,
    private val expandSection: (SendSections) -> Unit,
    private val emitFocusField: (SendFocusField) -> Unit,
    private val showLoading: () -> Unit,
    private val hideLoading: () -> Unit,
    private val showError: (UiText) -> Unit,
) : SendSubmitStrategy {

    private var submitJob: Job? = null

    override fun submit() {
        if (submitJob?.isActive == true) return
        if (addressFieldState.text.isBlank()) {
            expandSection(SendSections.Address)
            emitFocusField(SendFocusField.ADDRESS)
            return
        }
        if (tokenAmountFieldState.text.isBlank()) {
            expandSection(SendSections.Amount)
            emitFocusField(SendFocusField.AMOUNT)
            return
        }

        submitJob =
            scope.launch {
                showLoading()
                try {
                    val validated = accountValidator.validate()
                    val vaultId = validated.vaultId
                    val chain = validated.chain
                    val dstAddress = validated.dstAddress
                    val selectedAccount = validated.selectedAccount
                    val gasFee = validated.gasFee

                    val rawInput = addressFieldState.text.asAddressInput()
                    val labelCandidate = addressManager.dstAddressLabel.value ?: rawInput
                    val dstLabel =
                        labelCandidate.takeIf {
                            it.isNotBlank() &&
                                !chainAccountAddressRepository.isValid(chain, it) &&
                                chainAccountAddressRepository.isValid(chain, dstAddress)
                        }

                    if (!chainAccountAddressRepository.isValid(chain, dstAddress)) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_address)
                        )
                    }

                    val selectedTokenValue =
                        selectedAccount.tokenValue
                            ?: throw InvalidTransactionDataException(
                                UiText.StringResource(R.string.send_error_no_token)
                            )

                    val userMemo = memoFieldState.text.toString().takeIf { it.isNotEmpty() }

                    // XRP destination tag: from its own field, carried in the first-class proto
                    // field (not the memo). A non-empty non-canonical value is rejected so a bad
                    // paste can't send an untagged (or wrongly tagged) payment to an exchange.
                    val rawTag = destinationTagFieldState.text.toString().takeIf { it.isNotEmpty() }
                    val destinationTag =
                        if (chain == Chain.Ripple) {
                            if (RippleDestinationTag.isNonCanonicalTag(rawTag)) {
                                throw InvalidTransactionDataException(
                                    UiText.StringResource(
                                        R.string.send_error_xrp_invalid_destination_tag
                                    )
                                )
                            }
                            RippleDestinationTag.parseCanonicalDestinationTag(rawTag)
                        } else null

                    // Dual-write the tag into the memo when a tag is set and there's no distinct
                    // memo, so a not-yet-updated co-signer that only reads the legacy memo-as-tag
                    // carrier rebuilds the same DestinationTag (byte-identical sighash). Mirrors
                    // iOS
                    // dualWritingRippleTag; RippleHelper's memoEchoesTag drops this echo from the
                    // signed tx, so no on-chain Memos blob is added.
                    val memo =
                        if (chain == Chain.Ripple && destinationTag != null && userMemo == null) {
                            destinationTag.toString()
                        } else {
                            userMemo
                        }

                    val selectedToken = selectedAccount.token

                    val tokenAmount =
                        tokenAmountFieldState.text.toString().toPlainBigDecimalOrNull()
                    if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
                        throw InvalidTransactionDataException(
                            UiText.StringResource(R.string.send_error_no_amount)
                        )
                    }

                    val tokenAmountInt =
                        tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

                    val srcAddress = selectedToken.address
                    val isMaxAmount = tokenAmount.compareTo(amountManager.currentMaxAmount) == 0

                    if (chain == Chain.Tron) {
                        val isTronStakingOp =
                            memo != null &&
                                selectedToken.isNativeToken &&
                                TRON_STAKING_MEMO_REGEX.matches(memo)
                        if (!isTronStakingOp && srcAddress == dstAddress) {
                            throw InvalidTransactionDataException(
                                UiText.StringResource(R.string.send_error_same_address)
                            )
                        }
                    }

                    val isThorchainRouterDeposit =
                        defiTypeProvider() == DeFiNavActions.ADD_LP &&
                            !selectedToken.isNativeToken &&
                            selectedToken.chain.standard == TokenStandard.EVM

                    val specific =
                        withContext(Dispatchers.IO) {
                                blockChainSpecificRepository.getSpecific(
                                    chain = chain,
                                    address = srcAddress,
                                    token = selectedToken,
                                    gasFee = gasFee,
                                    // Reuse the memo captured above rather than re-reading the
                                    // field
                                    // on Dispatchers.IO: a keystroke landing between the two reads
                                    // could size the byteFee for a different memo than the one
                                    // embedded in Transaction.memo and signed.
                                    memo = memo,
                                    tokenAmountValue = tokenAmountInt,
                                    isSwap = false,
                                    isMaxAmountEnabled = isMaxAmount,
                                    isDeposit = false,
                                    dstAddress = dstAddress,
                                    isThorchainRouterDeposit = isThorchainRouterDeposit,
                                )
                            }
                            .let { applyGasSettings(it) }
                            .let {
                                applyBitcoinPlan(
                                    it,
                                    vaultId,
                                    selectedToken,
                                    dstAddress,
                                    tokenAmountInt,
                                    memo,
                                    chain,
                                )
                            }
                            .let { applyRippleDestinationTag(it, destinationTag) }

                    if (selectedToken.isNativeToken) {
                        val defiType = defiTypeProvider()
                        val availableTokenBalance =
                            if (defiType == DeFiNavActions.UNFREEZE_TRX) {
                                currentTronFrozenBalanceProvider()
                                    ?.movePointRight(selectedToken.decimal)
                                    ?.toBigInteger() ?: BigInteger.ZERO
                            } else {
                                getAvailableTokenBalance(selectedAccount, gasFee.value)?.value
                                    ?: BigInteger.ZERO
                            }

                        if (tokenAmountInt > availableTokenBalance) {
                            val errorRes =
                                if (defiType == DeFiNavActions.UNFREEZE_TRX) {
                                    R.string.send_error_insufficient_frozen_balance
                                } else {
                                    R.string.send_error_insufficient_native_balance_with_fees
                                }
                            throw InvalidTransactionDataException(
                                UiText.FormattedText(errorRes, listOf(selectedToken.ticker))
                            )
                        }

                        if (defiType == DeFiNavActions.UNFREEZE_TRX) {
                            val liquidForGas =
                                getAvailableTokenBalance(selectedAccount, BigInteger.ZERO)?.value
                                    ?: BigInteger.ZERO
                            if (liquidForGas < gasFee.value) {
                                throw InvalidTransactionDataException(
                                    UiText.FormattedText(
                                        R.string.send_error_insufficient_native_balance_with_fees,
                                        listOf(selectedToken.ticker),
                                    )
                                )
                            }
                        }

                        if (chain == Chain.Cardano) {
                            chainValidationService.validateCardanoUTXORequirements(
                                sendAmount = tokenAmountInt,
                                totalBalance = selectedTokenValue.value,
                                estimatedFee = gasFee.value,
                            )
                        }

                        if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                            chainValidationService.validateBtcLikeAmount(
                                tokenAmountInt,
                                chain,
                                planBtc.value,
                            )
                        }

                        withContext(Dispatchers.IO) {
                            chainValidationService.validateRippleDestinationReserve(
                                selectedToken = selectedToken,
                                dstAddress = dstAddress,
                                tokenAmountInt = tokenAmountInt,
                            )
                            chainValidationService.validateRippleDestinationTag(
                                selectedToken = selectedToken,
                                dstAddress = dstAddress,
                                // A canonical numeric memo (no dedicated tag) is signed as a
                                // DestinationTag by RippleHelper, so treat it as a present tag here
                                // instead of falsely blocking the send.
                                destinationTag =
                                    destinationTag
                                        ?: RippleDestinationTag.parseCanonicalDestinationTag(memo),
                            )
                        }
                    } else if (
                        chain == Chain.TerraClassic &&
                            TerraClassicTax.isBankDenom(
                                selectedToken.contractAddress,
                                selectedToken.isNativeToken,
                            )
                    ) {
                        // Terra Classic bank denoms (USTC/uusd) pay gas + burn tax in their OWN
                        // denom, not in native LUNC, so gate on the token's own balance covering
                        // amount + fee. Requiring a LUNC balance here (as the generic non-native
                        // branch below does) would wrongly block a USTC send from a vault with ~0
                        // LUNC, even though the chain deducts the fee from the USTC being sent.
                        if (selectedTokenValue.value < tokenAmountInt + gasFee.value) {
                            throw InvalidTransactionDataException(
                                UiText.FormattedText(
                                    R.string.send_error_insufficient_native_balance_with_fees,
                                    listOf(selectedToken.ticker),
                                )
                            )
                        }
                    } else {
                        val nativeTokenAccount =
                            accounts.value.find {
                                it.token.isNativeToken && it.token.chain == chain
                            }
                        val nativeTokenValue =
                            nativeTokenAccount?.tokenValue?.value
                                ?: throw InvalidTransactionDataException(
                                    UiText.StringResource(R.string.send_error_no_token)
                                )

                        if (selectedTokenValue.value < tokenAmountInt) {
                            throw InvalidTransactionDataException(
                                UiText.FormattedText(
                                    R.string.send_error_insufficient_native_balance_with_fees,
                                    listOf(selectedToken.ticker),
                                )
                            )
                        } else if (nativeTokenValue < gasFee.value) {
                            throw InvalidTransactionDataException(
                                UiText.FormattedText(
                                    R.string.insufficient_native_token,
                                    listOf(nativeTokenAccount.token.ticker),
                                )
                            )
                        }
                    }

                    val evmGasSettings = gasSettings.value as? GasSettings.Eth
                    val totalGasAndFee =
                        gasFeeToEstimatedFee(
                            GasFeeParams(
                                gasLimit =
                                    if (evmGasSettings != null) evmGasSettings.gasLimit
                                    else BigInteger.valueOf(1),
                                gasFee =
                                    selectGasFeeForFeeEstimation(
                                        chain = chain,
                                        gasFee = gasFee,
                                        planFee = planFee.value,
                                        evmGasSettings = evmGasSettings,
                                    ),
                                selectedToken = selectedToken,
                            )
                        )

                    val transaction =
                        Transaction(
                            id = UUID.randomUUID().toString(),
                            vaultId = vaultId,
                            chainId = chain.raw,
                            token = selectedToken,
                            srcAddress = srcAddress,
                            dstAddress = dstAddress,
                            dstLabel = dstLabel,
                            tokenValue =
                                TokenValue(
                                    value = tokenAmountInt,
                                    unit = selectedTokenValue.unit,
                                    decimals = selectedToken.decimal,
                                ),
                            fiatValue =
                                FiatValue(
                                    value =
                                        fiatAmountFieldState.text.toString().toBigDecimalOrNull()
                                            ?: BigDecimal.ZERO,
                                    currency = appCurrency.value.ticker,
                                ),
                            gasFee = gasFee,
                            blockChainSpecific = specific.blockChainSpecific,
                            utxos = specific.utxos,
                            memo = memo,
                            estimatedFee = totalGasAndFee.formattedFiatValue,
                            totalGas = totalGasAndFee.formattedTokenValue,
                        )

                    transactionRepository.addTransaction(transaction)

                    navigator.route(
                        Route.VerifySend(transactionId = transaction.id, vaultId = vaultId)
                    )
                } catch (e: InvalidTransactionDataException) {
                    showError(e.text)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    showError(
                        e.message?.asUiText()
                            ?: UiText.StringResource(R.string.dialog_default_error_body)
                    )
                } finally {
                    hideLoading()
                }
            }
    }

    private fun applyRippleDestinationTag(
        specific: BlockChainSpecificAndUtxo,
        destinationTag: UInt?,
    ): BlockChainSpecificAndUtxo {
        val ripple = specific.blockChainSpecific as? BlockChainSpecific.Ripple ?: return specific
        return specific.copy(blockChainSpecific = ripple.copy(destinationTag = destinationTag))
    }

    private fun applyGasSettings(it: BlockChainSpecificAndUtxo): BlockChainSpecificAndUtxo {
        val gs = gasSettings.value ?: return it
        val spec = it.blockChainSpecific
        return when {
            gs is GasSettings.Eth && spec is BlockChainSpecific.Ethereum ->
                it.copy(
                    blockChainSpecific =
                        spec.copy(
                            maxFeePerGasWei = gs.baseFee,
                            priorityFeeWei = gs.priorityFee,
                            gasLimit = gs.gasLimit,
                        )
                )

            gs is GasSettings.UTXO && spec is BlockChainSpecific.UTXO ->
                it.copy(blockChainSpecific = spec.copy(byteFee = gs.byteFee))

            else -> it
        }
    }

    private suspend fun applyBitcoinPlan(
        specific: BlockChainSpecificAndUtxo,
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        memo: String?,
        chain: Chain,
    ): BlockChainSpecificAndUtxo {
        if (chain.standard != TokenStandard.UTXO || chain == Chain.Cardano) return specific

        planBtc.value
            ?: bitcoinPlanService
                .getPlan(
                    vaultId = vaultId,
                    selectedToken = selectedToken,
                    dstAddress = dstAddress,
                    tokenAmountInt = tokenAmountInt,
                    specific = specific,
                    memo = memo,
                )
                .also { plan ->
                    planBtc.value = plan
                    planFee.value = plan.fee
                }

        return chainValidationService.selectUtxosIfNeeded(
            chain = chain,
            specific = specific,
            plan = planBtc.value,
        )
    }
}
