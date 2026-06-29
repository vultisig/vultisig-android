package com.vultisig.wallet.ui.models.deposit.submit

import com.vultisig.wallet.data.api.chains.ton.TonStakingApi
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.ValidateMayaTransactionHeightUseCase
import com.vultisig.wallet.ui.models.deposit.DepositFieldValidator
import com.vultisig.wallet.ui.models.deposit.DepositOption
import javax.inject.Inject
import wallet.core.jni.TONAddressConverter

/**
 * Builds the [DepositSubmitStrategies] map for a `DepositFormViewModel`.
 *
 * Hilt-provided repositories and use-cases are injected here once; the per-instance,
 * view-model-owned dependencies arrive via [DepositStrategyContext] on each [create] call.
 */
internal class DepositStrategyFactory
@Inject
constructor(
    private val accountsRepository: AccountsRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val feeServiceComposite: FeeServiceComposite,
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl,
    private val thorChainLpPreflight: ThorChainLpPreflightUseCase,
    private val validateMayaTransactionHeight: ValidateMayaTransactionHeightUseCase,
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase,
    private val fieldValidator: DepositFieldValidator,
    private val tonStakingApi: TonStakingApi,
) {

    /**
     * Creates the populated strategy map for [context], verifying every [DepositOption] is covered.
     *
     * @param context the view-model-owned dependencies shared across strategies.
     * @return a map from each [DepositOption] to the strategy that builds its transaction.
     */
    fun create(context: DepositStrategyContext): DepositSubmitStrategies {
        val fields = context.fields

        val strategies: DepositSubmitStrategies =
            mapOf(
                DepositOption.AddCacaoPool to
                    AddCacaoPoolStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        accountsRepository = accountsRepository,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.Bond to
                    BondStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        selectedTokenProvider = context.selectedToken,
                        nodeAddressFieldState = fields.nodeAddressFieldState,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        providerFieldState = fields.providerFieldState,
                        assetsFieldState = fields.assetsFieldState,
                        lpUnitsFieldState = fields.lpUnitsFieldState,
                        operatorFeeFieldState = fields.operatorFeeFieldState,
                        chainAccountAddressRepository = chainAccountAddressRepository,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        isAssetCharsValid = isAssetCharsValid,
                        isLpUnitCharsValid = fieldValidator::isLpUnitCharsValid,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.Unbond to
                    UnbondStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        selectedTokenProvider = context.selectedToken,
                        nodeAddressFieldState = fields.nodeAddressFieldState,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        providerFieldState = fields.providerFieldState,
                        assetsFieldState = fields.assetsFieldState,
                        lpUnitsFieldState = fields.lpUnitsFieldState,
                        chainAccountAddressRepository = chainAccountAddressRepository,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        isAssetCharsValid = isAssetCharsValid,
                        isLpUnitCharsValid = fieldValidator::isLpUnitCharsValid,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.Leave to
                    LeaveStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        selectedTokenProvider = context.selectedToken,
                        nodeAddressFieldState = fields.nodeAddressFieldState,
                        chainAccountAddressRepository = chainAccountAddressRepository,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.Custom to
                    CustomStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        selectedTokenProvider = context.selectedToken,
                        customMemoFieldState = fields.customMemoFieldState,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.Stake to
                    StakeStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        nodeAddressFieldState = fields.nodeAddressFieldState,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        accountsRepository = accountsRepository,
                        tonStakingApi = tonStakingApi,
                        toBounceableAddress = ::toTonBounceableAddress,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.Unstake to
                    UnstakeStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        nodeAddressFieldState = fields.nodeAddressFieldState,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        accountsRepository = accountsRepository,
                        tonStakingApi = tonStakingApi,
                        toBounceableAddress = ::toTonBounceableAddress,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.TransferIbc to
                    TransferIbcStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        addressProvider = context.address,
                        nodeAddressFieldState = fields.nodeAddressFieldState,
                        customMemoFieldState = fields.customMemoFieldState,
                        dstAddressErrorOrNull = fieldValidator::dstAddressErrorOrNull,
                        requireTokenAmount = context.requireTokenAmount,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.Switch to
                    SwitchStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        addressProvider = context.address,
                        nodeAddressFieldState = fields.nodeAddressFieldState,
                        thorAddressFieldState = fields.thorAddressFieldState,
                        dstAddressErrorOrNull = fieldValidator::dstAddressErrorOrNull,
                        requireTokenAmount = context.requireTokenAmount,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.Merge to
                    MergeStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        addressProvider = context.address,
                        requireTokenAmount = context.requireTokenAmount,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.UnMerge to
                    UnMergeStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        addressProvider = context.address,
                        rujiMergeBalancesProvider = context.rujiMergeBalances,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.RemoveCacaoPool to
                    RemoveCacaoPoolStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        accountsRepository = accountsRepository,
                        validateMayaTransactionHeight = validateMayaTransactionHeight,
                        validateBasisPoints = fieldValidator::validateBasisPoints,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.AddLiquidity to
                    AddLiquidityStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        lpPoolIdProvider = context.lpPoolId,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        accountsRepository = accountsRepository,
                        thorChainLpPreflight = thorChainLpPreflight,
                        resolvePairedAddress = context.resolvePairedAddress,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.RemoveLiquidity to
                    RemoveLiquidityStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        lpPoolIdProvider = context.lpPoolId,
                        stateProvider = context.state,
                        accountsRepository = accountsRepository,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        calculateGasFee = context.calculateGasFee,
                        getFeesFiatValue = context.getFeesFiatValue,
                    ),
                DepositOption.SecuredAsset to
                    SecuredAssetStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        thorAddressFieldState = fields.thorAddressFieldState,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        selectedAccountProvider = context.selectedAccount,
                        resolveInboundAddress = context.resolveSecuredAssetInboundAddress,
                        vaultRepository = vaultRepository,
                        feeServiceComposite = feeServiceComposite,
                        tokenRepository = tokenRepository,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        gasFeeToEstimate = gasFeeToEstimate,
                        getBitcoinTransactionPlan = context.getBitcoinTransactionPlan,
                    ),
                DepositOption.WithdrawSecuredAsset to
                    WithdrawSecuredAssetStrategy(
                        vaultIdProvider = context.vaultId,
                        chainProvider = context.chain,
                        stateProvider = context.state,
                        thorAddressFieldState = fields.thorAddressFieldState,
                        tokenAmountFieldState = fields.tokenAmountFieldState,
                        accountsRepository = accountsRepository,
                        vaultRepository = vaultRepository,
                        feeServiceComposite = feeServiceComposite,
                        tokenRepository = tokenRepository,
                        blockChainSpecificRepository = context.blockChainSpecificRepository,
                        gasFeeToEstimate = gasFeeToEstimate,
                    ),
            )

        check(DepositOption.entries.all { it in strategies }) {
            "Missing deposit strategy for: " +
                DepositOption.entries.filterNot { it in strategies.keys }
        }

        return strategies
    }
}

/**
 * Converts a TON pool address (raw `0:…` or user-friendly) to the bounceable `EQ…` form required
 * for nominator-pool deposits/withdrawals — a non-bounceable message a pool rejects is absorbed
 * (lost).
 */
private fun toTonBounceableAddress(address: String): String =
    TONAddressConverter.toUserFriendly(address, /* bounceable= */ true, /* testnet= */ false)
