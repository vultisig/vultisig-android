package com.vultisig.wallet.ui.models.send.usecases

import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GetAvailableTokenBalanceUseCase
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.GasSettings
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.utils.UiText
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import timber.log.Timber
import wallet.core.jni.proto.Bitcoin

/** Result of a plan fee calculation for UTXO chains. */
internal data class PlanFeeResult(val planFee: Long, val planBtc: Bitcoin.TransactionPlan?)

/** Result of a percentage amount calculation, including an optionally refreshed gas fee. */
internal data class PercentageAmountResult(val amount: BigDecimal, val updatedGasFee: TokenValue?)

/** Result of an estimated fee calculation for UI display. */
internal data class EstimatedFeeUiResult(val estimatedFee: String, val totalGas: String)

/** Encapsulates amount and gas calculation logic extracted from SendFormViewModel. */
@OptIn(FlowPreview::class)
internal class CalculationUseCase
@Inject
constructor(
    private val feeServiceComposite: FeeServiceComposite,
    private val tokenRepository: TokenRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val mapTokenValueToString: TokenValueToStringWithUnitMapper,
    private val addressParserRepository: AddressParserRepository,
    private val vaultRepository: VaultRepository,
    private val getAvailableTokenBalance: GetAvailableTokenBalanceUseCase,
) {

    /** Returns a gas fee adjusted by the active gas settings override. */
    fun adjustGasFee(
        gasFee: TokenValue,
        gasSettings: GasSettings?,
        spec: BlockChainSpecificAndUtxo?,
    ): TokenValue =
        gasFee.copy(
            value =
                if (
                    gasSettings is GasSettings.UTXO &&
                        spec?.blockChainSpecific is BlockChainSpecific.UTXO
                ) {
                    gasSettings.byteFee
                } else gasFee.value
        )

    /** Returns the EVM gas limit from blockchain-specific data, defaulting to 1. */
    fun calculateGasLimit(chain: Chain, specific: BlockChainSpecific?): BigInteger =
        (specific as? BlockChainSpecific.Ethereum)?.gasLimit ?: BigInteger.valueOf(1)

    /**
     * Emits the formatted gas token balance for the selected non-native token, or null for native
     * tokens.
     */
    fun gasTokenBalanceFlow(
        selectedToken: StateFlow<Coin?>,
        accounts: StateFlow<List<Account>>,
    ): Flow<UiText?> =
        selectedToken.filterNotNull().map { token ->
            if (token.isNativeToken) null
            else
                accounts.value
                    .find { account ->
                        account.token.isNativeToken && account.token.chain == token.chain
                    }
                    ?.tokenValue
                    ?.let { UiText.DynamicString(mapTokenValueToString(it)) }
        }

    /**
     * Emits an adjusted gas fee whenever token, address, memo, amount, or fee-related settings
     * change. Debounces input at 350 ms. Errors in fee calculation are logged and suppressed.
     *
     * @param recalculate optional trigger flow; each new emission forces a recalculation even when
     *   other inputs are unchanged (e.g. from a manual refresh action).
     */
    fun gasFeesFlow(
        selectedToken: StateFlow<Coin?>,
        addressFlow: Flow<CharSequence>,
        memoFlow: Flow<CharSequence>,
        tokenAmountFlow: Flow<CharSequence>,
        gasSettings: StateFlow<GasSettings?>,
        specific: StateFlow<BlockChainSpecificAndUtxo?>,
        resolvedDstAddress: StateFlow<String?>,
        vault: () -> Vault?,
        recalculate: Flow<Long> = flowOf(0L),
    ): Flow<TokenValue> =
        combine(
            selectedToken
                .filterNotNull()
                .combine(addressFlow) { token, dst -> token to dst.toString() }
                .combine(memoFlow) { (token, dst), memo -> Triple(token, dst, memo.toString()) }
                .combine(tokenAmountFlow) { (token, dst, memo), tokenAmountText ->
                    Triple(token, dst, memo) to tokenAmountText
                }
                .debounce(350)
                .distinctUntilChanged()
                .combine(recalculate) { data, _ -> data }
                .mapNotNull { (triple, tokenAmount) ->
                    val (token, dst, memo) = triple
                    val currentVault = vault() ?: return@mapNotNull null

                    val tokenAmountDecimal = tokenAmount.toString().toBigDecimalOrNull()
                    val tokenAmountInt =
                        tokenAmountDecimal?.movePointRight(token.decimal)?.toBigInteger()
                            ?: return@mapNotNull null

                    val chain = token.chain
                    val blockchainTransaction =
                        Transfer(
                            coin = token,
                            vault =
                                VaultData(
                                    vaultHexChainCode = currentVault.hexChainCode,
                                    vaultHexPublicKey = currentVault.getPubKeyByChain(chain),
                                ),
                            amount = tokenAmountInt,
                            to = resolvedDstAddress.value ?: dst,
                            memo = memo,
                            isMax = false,
                        )

                    val fees =
                        withContext(Dispatchers.IO) {
                            feeServiceComposite.calculateFees(blockchainTransaction)
                        }
                    val nativeCoin =
                        withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }

                    TokenValue(value = fees.amount, token = nativeCoin)
                }
                .catch { Timber.e(it) },
            gasSettings,
            specific,
        ) { rawFee, settings, spec ->
            adjustGasFee(rawFee, settings, spec)
        }

    /** Emits formatted estimated fee and total gas strings for display. */
    fun estimatedFeeFlow(
        selectedToken: StateFlow<Coin?>,
        gasFee: StateFlow<TokenValue?>,
        gasSettings: StateFlow<GasSettings?>,
        planFee: StateFlow<Long?>,
    ): Flow<EstimatedFeeUiResult> =
        combine(
            selectedToken.filterNotNull(),
            gasFee.filterNotNull(),
            gasSettings,
            planFee.filterNotNull(),
        ) { token, fee, settings, plan ->
            val chain = token.chain
            val estimated =
                gasFeeToEstimatedFee(
                    GasFeeParams(
                        gasLimit =
                            if (chain.standard == TokenStandard.EVM) {
                                if (settings is GasSettings.Eth) settings.gasLimit
                                else BigInteger.valueOf(1)
                            } else {
                                BigInteger.valueOf(1)
                            },
                        gasFee =
                            if (chain.standard == TokenStandard.UTXO) {
                                fee.copy(value = BigInteger.valueOf(plan))
                            } else fee,
                        selectedToken = token,
                        perUnit = true,
                    )
                )
            EstimatedFeeUiResult(
                estimatedFee = estimated.formattedFiatValue,
                totalGas = estimated.formattedTokenValue,
            )
        }

    /**
     * Emits a [PlanFeeResult] whenever the UTXO transaction plan changes. For non-UTXO chains,
     * emits a placeholder result with planFee = 1. Errors are logged and the result is null
     * (skipped by the caller).
     */
    fun planFeeFlow(
        selectedToken: StateFlow<Coin?>,
        addressFlow: Flow<CharSequence>,
        tokenAmountFlow: Flow<CharSequence>,
        specific: StateFlow<BlockChainSpecificAndUtxo?>,
        memoFlow: Flow<CharSequence>,
        vaultId: () -> String?,
    ): Flow<PlanFeeResult?> =
        combine(
            selectedToken.filterNotNull(),
            addressFlow,
            tokenAmountFlow,
            specific.filterNotNull(),
            memoFlow,
        ) { token, dstAddress, tokenAmount, spec, memo ->
            try {
                val chain = token.chain
                if (chain.standard != TokenStandard.UTXO || chain == Chain.Cardano) {
                    return@combine PlanFeeResult(planFee = 1L, planBtc = null)
                }

                val tokenAmountInt =
                    tokenAmount
                        .toString()
                        .toBigDecimalOrNull()
                        ?.movePointRight(token.decimal)
                        ?.toBigInteger() ?: return@combine null

                val id = vaultId() ?: error("No vault ID for plan fee calculation")
                val resolvedAddress =
                    addressParserRepository.resolveName(dstAddress.toString(), chain)

                val plan =
                    getBitcoinTransactionPlan(
                        vaultId = id,
                        selectedToken = token,
                        dstAddress = resolvedAddress,
                        tokenAmountInt = tokenAmountInt,
                        specific = spec,
                        memo = memo.toString(),
                    )
                PlanFeeResult(planFee = plan.fee, planBtc = plan)
            } catch (e: Exception) {
                Timber.e(e)
                null
            }
        }

    /**
     * Emits an updated [BlockChainSpecificAndUtxo] with the sendMaxAmount flag toggled when the
     * isMaxAmount flag changes on UTXO chains. Non-UTXO chains are ignored.
     */
    fun maxAmountSpecificFlow(
        isMaxAmount: Flow<Boolean>,
        selectedAccount: () -> Account?,
        specific: StateFlow<BlockChainSpecificAndUtxo?>,
    ): Flow<BlockChainSpecificAndUtxo> =
        combine(isMaxAmount, specific) { isMax, spec ->
                val chain = selectedAccount()?.token?.chain ?: return@combine null
                if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                    val utxoSpec =
                        spec?.blockChainSpecific as? BlockChainSpecific.UTXO ?: return@combine null
                    spec.copy(blockChainSpecific = utxoSpec.copy(sendMaxAmount = isMax))
                } else null
            }
            .filterNotNull()
            .distinctUntilChanged()

    /**
     * Calculates the token amount corresponding to [percentage] of the available balance, fetching
     * an accurate gas fee estimate if not already cached.
     *
     * @return the computed amount and the refreshed gas fee (null if no refresh occurred).
     */
    suspend fun calculatePercentageWithAccurateFee(
        percentage: Float,
        vault: Vault?,
        selectedAccount: Account?,
        gasFee: TokenValue?,
        gasSettings: StateFlow<GasSettings?>,
        specific: StateFlow<BlockChainSpecificAndUtxo?>,
        defiType: DeFiNavActions?,
        addressText: String,
        memoText: String,
    ): PercentageAmountResult {
        vault ?: return PercentageAmountResult(BigDecimal.ZERO, null)
        selectedAccount ?: return PercentageAmountResult(BigDecimal.ZERO, null)
        val isMax = percentage == 1f
        val token = selectedAccount.token

        var amount =
            if (gasFee != null) {
                fetchPercentageOfAvailableBalance(percentage, selectedAccount, gasFee, defiType)
            } else {
                getAvailableTokenBalance(selectedAccount, BigInteger.ZERO)
                    ?.decimal
                    ?.multiply(percentage.toBigDecimal()) ?: BigDecimal.ZERO
            }

        if (
            defiType != null &&
                defiType != DeFiNavActions.BOND &&
                defiType != DeFiNavActions.STAKE_RUJI &&
                defiType != DeFiNavActions.UNSTAKE_RUJI &&
                defiType != DeFiNavActions.STAKE_TCY &&
                defiType != DeFiNavActions.UNSTAKE_TCY &&
                defiType != DeFiNavActions.STAKE_STCY &&
                defiType != DeFiNavActions.UNSTAKE_STCY &&
                defiType != DeFiNavActions.REDEEM_YRUNE &&
                defiType != DeFiNavActions.MINT_YTCY &&
                defiType != DeFiNavActions.REDEEM_YTCY &&
                defiType != DeFiNavActions.FREEZE_TRX &&
                defiType != DeFiNavActions.UNFREEZE_TRX
        ) {
            return PercentageAmountResult(amount, null)
        }

        val chain = token.chain

        if (gasFee != null && chain.standard == TokenStandard.EVM) {
            return PercentageAmountResult(amount, null)
        }

        try {
            val tokenAmountInt = amount.movePointRight(token.decimal).toBigInteger()
            val blockchainTransaction =
                Transfer(
                    coin = token,
                    vault =
                        VaultData(
                            vaultHexChainCode = vault.hexChainCode,
                            vaultHexPublicKey = vault.getPubKeyByChain(chain),
                        ),
                    amount = tokenAmountInt,
                    to = addressText,
                    memo = memoText,
                    isMax = isMax,
                )

            val calculatedFee =
                withContext(Dispatchers.IO) {
                    feeServiceComposite.calculateFees(blockchainTransaction)
                }

            val nativeCoin =
                withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }

            val newGasFee = TokenValue(value = calculatedFee.amount, token = nativeCoin)
            val updatedGasFee = adjustGasFee(newGasFee, gasSettings.value, specific.value)
            amount =
                fetchPercentageOfAvailableBalance(
                    percentage,
                    selectedAccount,
                    updatedGasFee,
                    defiType,
                )
            return PercentageAmountResult(amount, updatedGasFee)
        } catch (e: Exception) {
            Timber.e(e, "Failed to calculate gas fee for percentage amount")
        }

        return PercentageAmountResult(amount, null)
    }

    /** Resolves the Bitcoin transaction plan for a UTXO send. */
    internal suspend fun getBitcoinTransactionPlan(
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specific: BlockChainSpecificAndUtxo,
        memo: String?,
    ): Bitcoin.TransactionPlan {
        val vault = vaultRepository.get(vaultId) ?: error("Can't calculate plan fees")

        val keysignPayload =
            KeysignPayload(
                coin = selectedToken,
                toAddress = dstAddress,
                toAmount = tokenAmountInt,
                blockChainSpecific = specific.blockChainSpecific,
                memo = memo,
                vaultPublicKeyECDSA = vault.pubKeyECDSA,
                vaultLocalPartyID = vault.localPartyID,
                utxos = specific.utxos,
                libType = vault.libType,
                wasmExecuteContractPayload = null,
            )

        val utxo = UtxoHelper.getHelper(vault, keysignPayload.coin.coinType)
        return utxo.getBitcoinTransactionPlan(keysignPayload)
    }

    private suspend fun fetchPercentageOfAvailableBalance(
        percentage: Float,
        selectedAccount: Account,
        gasFee: TokenValue?,
        defiType: DeFiNavActions?,
    ): BigDecimal {
        val currentGasFee = gasFee ?: return BigDecimal.ZERO

        val availableTokenBalance =
            if (
                defiType == null ||
                    defiType == DeFiNavActions.BOND ||
                    defiType == DeFiNavActions.STAKE_RUJI ||
                    defiType == DeFiNavActions.STAKE_TCY ||
                    defiType == DeFiNavActions.STAKE_STCY ||
                    defiType == DeFiNavActions.MINT_YRUNE ||
                    defiType == DeFiNavActions.REDEEM_YRUNE ||
                    defiType == DeFiNavActions.MINT_YTCY ||
                    defiType == DeFiNavActions.REDEEM_YTCY ||
                    defiType == DeFiNavActions.FREEZE_TRX ||
                    defiType == DeFiNavActions.UNFREEZE_TRX
            ) {
                getAvailableTokenBalance(selectedAccount, currentGasFee.value)
            } else {
                getAvailableTokenBalance(selectedAccount, BigInteger.ZERO)
            }

        return availableTokenBalance
            ?.decimal
            ?.multiply(percentage.toBigDecimal())
            ?.setScale(selectedAccount.token.decimal, RoundingMode.DOWN)
            ?.stripTrailingZeros() ?: BigDecimal.ZERO
    }
}
