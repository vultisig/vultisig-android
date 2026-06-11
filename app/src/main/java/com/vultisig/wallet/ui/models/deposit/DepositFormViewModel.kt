package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.api.models.thorchain.MergeAccount
import com.vultisig.wallet.data.api.models.thorchain.RujiStakeBalances
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper.Companion.SECURE_ASSETS_TICKERS
import com.vultisig.wallet.data.crypto.getChainName
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.TokenStandard
import com.vultisig.wallet.data.models.TokenValue
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.getDustThreshold
import com.vultisig.wallet.data.models.getPubKeyByChain
import com.vultisig.wallet.data.models.isSecuredAsset
import com.vultisig.wallet.data.models.payload.BlockChainSpecific
import com.vultisig.wallet.data.models.payload.KeysignPayload
import com.vultisig.wallet.data.models.payload.UtxoInfo
import com.vultisig.wallet.data.models.ticker
import com.vultisig.wallet.data.models.toValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.AppCurrencyRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenPriceRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.usecases.GetMayaCacaoMaturityStatusUseCase
import com.vultisig.wallet.data.usecases.GetThorChainLpPositionUseCase
import com.vultisig.wallet.data.usecases.MayaCacaoMaturityStatus
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.data.usecases.ThorChainLpPreflightUseCase
import com.vultisig.wallet.data.usecases.ValidateMayaTransactionHeightUseCase
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.models.defi.parseThorChainPool
import com.vultisig.wallet.ui.models.deposit.submit.AddCacaoPoolStrategy
import com.vultisig.wallet.ui.models.deposit.submit.AddLiquidityStrategy
import com.vultisig.wallet.ui.models.deposit.submit.BondStrategy
import com.vultisig.wallet.ui.models.deposit.submit.CustomStrategy
import com.vultisig.wallet.ui.models.deposit.submit.DepositSubmitStrategies
import com.vultisig.wallet.ui.models.deposit.submit.DepositSubmitStrategy
import com.vultisig.wallet.ui.models.deposit.submit.LeaveStrategy
import com.vultisig.wallet.ui.models.deposit.submit.MergeStrategy
import com.vultisig.wallet.ui.models.deposit.submit.RemoveCacaoPoolStrategy
import com.vultisig.wallet.ui.models.deposit.submit.RemoveLiquidityStrategy
import com.vultisig.wallet.ui.models.deposit.submit.SecuredAssetStrategy
import com.vultisig.wallet.ui.models.deposit.submit.StakeStrategy
import com.vultisig.wallet.ui.models.deposit.submit.SwitchStrategy
import com.vultisig.wallet.ui.models.deposit.submit.TransferIbcStrategy
import com.vultisig.wallet.ui.models.deposit.submit.UnMergeStrategy
import com.vultisig.wallet.ui.models.deposit.submit.UnbondStrategy
import com.vultisig.wallet.ui.models.deposit.submit.UnstakeStrategy
import com.vultisig.wallet.ui.models.deposit.submit.WithdrawSecuredAssetStrategy
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.screens.select.AssetSelected
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.parseDepositType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import com.vultisig.wallet.ui.utils.cacaoUnlocksInUiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Bitcoin
import wallet.core.jni.proto.Common.SigningError

internal enum class DepositOption {
    AddCacaoPool,
    Bond,
    Unbond,
    Leave,
    Stake,
    Unstake,
    Custom,
    TransferIbc,
    Switch,
    Merge,
    RemoveCacaoPool,
    UnMerge,
    SecuredAsset,
    WithdrawSecuredAsset,
    AddLiquidity,
    RemoveLiquidity,
}

@Immutable
internal data class DepositFormUiModel(
    val selectedToken: Coin = Coins.ThorChain.RUNE,
    val depositMessage: UiText = UiText.Empty,
    val depositOption: DepositOption = DepositOption.Bond,
    val depositOptions: List<DepositOption> = emptyList(),
    val depositChain: Chain? = null,
    val errorText: UiText? = null,
    val tokenAmountError: UiText? = null,
    val nodeAddressError: UiText? = null,
    val providerError: UiText? = null,
    val operatorFeeError: UiText? = null,
    val customMemoError: UiText? = null,
    val basisPointsError: UiText? = null,
    val assetsError: UiText? = null,
    val lpUnitsError: UiText? = null,
    val slippageError: UiText? = null,
    val isLoading: Boolean = false,
    val isCheckingWhitelist: Boolean = false,
    val isWhitelistFailed: Boolean = false,
    val balance: UiText = UiText.Empty,
    val balanceDecimal: BigDecimal? = null,
    val sharesBalance: UiText = R.string.share_balance_loading.asUiText(),
    val selectedDstChain: Chain = Chain.ThorChain,
    val dstChainList: List<Chain> = emptyList(),
    val dstAddressError: UiText? = null,
    val amountError: UiText? = null,
    val memoError: UiText? = null,
    val thorAddressError: UiText? = null,
    val selectedCoin: TokenMergeInfo = tokensToMerge.first(),
    val selectedUnMergeCoin: TokenMergeInfo = tokensToMerge.first(),
    val coinList: List<TokenMergeInfo> = tokensToMerge,
    val unstakableAmount: String? = null,
    val isUnstakeMature: Boolean = false,
    val unstakeUnlocksInText: UiText? = null,
    val rewardsAmount: String? = null,
    val availableSecuredAssets: List<TokenWithdrawSecureAsset> = emptyList(),
    val securedAssetsLoaded: Boolean = false,
    val selectedSecuredAsset: TokenWithdrawSecureAsset =
        availableSecuredAssets.firstOrNull() ?: TokenWithdrawSecureAsset.EMPTY,
    val bondableAssets: List<String> = emptyList(),
    val selectedBondAsset: String = "",
    val availableLpUnits: String? = null,
    // For Maya: total LP units in the pool. For THORChain remove-LP, this stores the user's own
    // units (the calculator divides by it so that selectedUnits/userUnits gives the redeem
    // fraction).
    val removeLpUnitsDivisor: BigInteger = BigInteger.ZERO,
    // For Maya: pool's CACAO depth (base units). For THORChain remove-LP, this stores the user's
    // pre-computed RUNE redeem value (base units). Renamed from chain-specific names because the
    // semantic differs between flows.
    val removeLpPoolDepth: BigInteger = BigInteger.ZERO,
    val removeLpDecimals: Int = RemoveLpCalculator.CACAO_DECIMALS,
    val removeLpTokenSymbol: String = "CACAO",
    val totalGas: UiText = UiText.Empty,
    val estimatedFee: UiText = UiText.Empty,
    val removeLpPercent: Float = 0f,
    // Slider-derived withdrawal fraction in basis points (0..10000). Stored so the submit path can
    // reuse the exact value used to compute the displayed redeem amount, keeping the shown amount
    // and the on-chain memo in sync at sub-percent granularity.
    val removeLpBasisPoints: Int = 0,
    val removeLpCacaoDisplay: String = "",
)

@HiltViewModel
internal class DepositFormViewModel
@Inject
constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,
    private val requestQrScan: RequestQrScanUseCase,
    appCurrencyRepository: AppCurrencyRepository,
    private val tokenPriceRepository: TokenPriceRepository,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val accountsRepository: AccountsRepository,
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase,
    private val requestResultRepository: RequestResultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val transactionRepository: DepositTransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val mayachainBondRepository: MayachainBondRepository,
    private val balanceRepository: BalanceRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val validateMayaTransactionHeight: ValidateMayaTransactionHeightUseCase,
    private val getMayaCacaoMaturityStatus: GetMayaCacaoMaturityStatusUseCase,
    private val feeServiceComposite: FeeServiceComposite,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
    private val getThorChainLpPositionUseCase: GetThorChainLpPositionUseCase,
    private val thorChainLpPreflight: ThorChainLpPreflightUseCase,
) : ViewModel() {

    private val appCurrency =
        appCurrencyRepository.currency.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(),
            appCurrencyRepository.defaultCurrency,
        )

    val fiatCurrency =
        appCurrency
            .map { it.ticker }
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(),
                appCurrencyRepository.defaultCurrency.ticker,
            )

    private var lastTokenValueUserInput: String = ""
    private var lastFiatValueUserInput: String = ""
    private var amountChangesJob: Job? = null

    private var vaultId: String? = null
    private var chain: Chain? = null

    private var rujiMergeBalances = MutableStateFlow<List<MergeAccount>?>(null)
    private var rujiStakeBalances = MutableStateFlow<RujiStakeBalances?>(null)
    private var planBtc: Bitcoin.TransactionPlan? = null

    val tokenAmountFieldState = TextFieldState()
    val fiatAmountFieldState = TextFieldState()
    val nodeAddressFieldState = TextFieldState()
    val providerFieldState = TextFieldState()
    val operatorFeeFieldState = TextFieldState()
    val customMemoFieldState = TextFieldState()
    val basisPointsFieldState = TextFieldState()
    val lpUnitsFieldState = TextFieldState()
    val assetsFieldState = TextFieldState()
    private var lpBondPoolMap: Map<String, com.vultisig.wallet.data.repositories.LpBondablePool> =
        emptyMap()
    val thorAddressFieldState = TextFieldState()
    val rewardsAmountFieldState = TextFieldState()
    val slippageFieldState = TextFieldState()

    private val _state = MutableStateFlow(DepositFormUiModel())
    val state: StateFlow<DepositFormUiModel> = _state.asStateFlow()
    var isLoading: Boolean
        get() = state.value.isLoading
        set(value) {
            _state.update { it.copy(isLoading = value) }
        }

    private val address = MutableStateFlow<Address?>(null)
    private var addressJob: Job? = null
    private var securedAssetThorAddressJob: Job? = null
    private var whitelistJob: Job? = null
    private var loadLpJob: Job? = null
    private var switchInboundJob: Job? = null
    private var withdrawSecuredAssetJob: Job? = null
    private var cacaoMaturityJob: Job? = null
    private var depositTypeAction: String? = null
    private var bondAddress: String? = null
    private var lpPoolId: String? = null

    private val bondStrategy: DepositSubmitStrategy =
        BondStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            selectedTokenProvider = ::getSelectedToken,
            nodeAddressFieldState = nodeAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            providerFieldState = providerFieldState,
            assetsFieldState = assetsFieldState,
            lpUnitsFieldState = lpUnitsFieldState,
            operatorFeeFieldState = operatorFeeFieldState,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            isAssetCharsValid = isAssetCharsValid,
            isLpUnitCharsValid = ::isLpUnitCharsValid,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val unbondStrategy: DepositSubmitStrategy =
        UnbondStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            selectedTokenProvider = ::getSelectedToken,
            nodeAddressFieldState = nodeAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            providerFieldState = providerFieldState,
            assetsFieldState = assetsFieldState,
            lpUnitsFieldState = lpUnitsFieldState,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            isAssetCharsValid = isAssetCharsValid,
            isLpUnitCharsValid = ::isLpUnitCharsValid,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val leaveStrategy: DepositSubmitStrategy =
        LeaveStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            selectedTokenProvider = ::getSelectedToken,
            nodeAddressFieldState = nodeAddressFieldState,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val stakeStrategy: DepositSubmitStrategy =
        StakeStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            nodeAddressFieldState = nodeAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val unstakeStrategy: DepositSubmitStrategy =
        UnstakeStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            nodeAddressFieldState = nodeAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            chainAccountAddressRepository = chainAccountAddressRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val mergeStrategy: DepositSubmitStrategy =
        MergeStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            addressProvider = { address.value },
            requireTokenAmount = ::requireTokenAmount,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val unMergeStrategy: DepositSubmitStrategy =
        UnMergeStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            addressProvider = { address.value },
            rujiMergeBalancesProvider = { rujiMergeBalances.value },
            tokenAmountFieldState = tokenAmountFieldState,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val switchStrategy: DepositSubmitStrategy =
        SwitchStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            addressProvider = { address.value },
            nodeAddressFieldState = nodeAddressFieldState,
            thorAddressFieldState = thorAddressFieldState,
            dstAddressErrorOrNull = ::dstAddressErrorOrNull,
            requireTokenAmount = ::requireTokenAmount,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val transferIbcStrategy: DepositSubmitStrategy =
        TransferIbcStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            addressProvider = { address.value },
            nodeAddressFieldState = nodeAddressFieldState,
            customMemoFieldState = customMemoFieldState,
            dstAddressErrorOrNull = ::dstAddressErrorOrNull,
            requireTokenAmount = ::requireTokenAmount,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val addCacaoPoolStrategy: DepositSubmitStrategy =
        AddCacaoPoolStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val removeCacaoPoolStrategy: DepositSubmitStrategy =
        RemoveCacaoPoolStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            validateMayaTransactionHeight = validateMayaTransactionHeight,
            validateBasisPoints = ::validateBasisPoints,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val addLiquidityStrategy: DepositSubmitStrategy =
        AddLiquidityStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            lpPoolIdProvider = { lpPoolId },
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            thorChainLpPreflight = thorChainLpPreflight,
            resolvePairedAddress = ::resolvePairedAddress,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val removeLiquidityStrategy: DepositSubmitStrategy =
        RemoveLiquidityStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            lpPoolIdProvider = { lpPoolId },
            stateProvider = { state.value },
            accountsRepository = accountsRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val securedAssetStrategy: DepositSubmitStrategy =
        SecuredAssetStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            thorAddressFieldState = thorAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            selectedAccountProvider = ::getSelectedAccount,
            resolveInboundAddress = ::requireSecuredAssetInboundAddress,
            vaultRepository = vaultRepository,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            gasFeeToEstimate = gasFeeToEstimate,
            planBtcProvider = { planBtc },
            setPlanBtc = { planBtc = it },
            getBitcoinTransactionPlan = ::getBitcoinTransactionPlan,
            selectUtxosIfNeeded = ::selectUtxosIfNeeded,
            validateBtcLikeAmount = ::validateBtcLikeAmount,
        )

    private val withdrawSecuredAssetStrategy: DepositSubmitStrategy =
        WithdrawSecuredAssetStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            stateProvider = { state.value },
            thorAddressFieldState = thorAddressFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            accountsRepository = accountsRepository,
            vaultRepository = vaultRepository,
            feeServiceComposite = feeServiceComposite,
            tokenRepository = tokenRepository,
            blockChainSpecificRepository = blockChainSpecificRepository,
            gasFeeToEstimate = gasFeeToEstimate,
        )

    private val customStrategy: DepositSubmitStrategy =
        CustomStrategy(
            vaultIdProvider = { vaultId },
            chainProvider = { chain },
            selectedTokenProvider = ::getSelectedToken,
            customMemoFieldState = customMemoFieldState,
            tokenAmountFieldState = tokenAmountFieldState,
            blockChainSpecificRepository = blockChainSpecificRepository,
            calculateGasFee = ::calculateGasFee,
            getFeesFiatValue = ::getFeesFiatValue,
        )

    private val depositStrategies: DepositSubmitStrategies =
        mapOf(
                DepositOption.AddCacaoPool to addCacaoPoolStrategy,
                DepositOption.Bond to bondStrategy,
                DepositOption.Unbond to unbondStrategy,
                DepositOption.Leave to leaveStrategy,
                DepositOption.Custom to customStrategy,
                DepositOption.Stake to stakeStrategy,
                DepositOption.Unstake to unstakeStrategy,
                DepositOption.TransferIbc to transferIbcStrategy,
                DepositOption.Switch to switchStrategy,
                DepositOption.Merge to mergeStrategy,
                DepositOption.UnMerge to unMergeStrategy,
                DepositOption.RemoveCacaoPool to removeCacaoPoolStrategy,
                DepositOption.AddLiquidity to addLiquidityStrategy,
                DepositOption.RemoveLiquidity to removeLiquidityStrategy,
                DepositOption.SecuredAsset to securedAssetStrategy,
                DepositOption.WithdrawSecuredAsset to withdrawSecuredAssetStrategy,
            )
            .also { strategies ->
                check(DepositOption.entries.all { it in strategies }) {
                    "Missing deposit strategy for: " +
                        DepositOption.entries.filterNot { it in strategies.keys }
                }
            }

    fun loadData(
        vaultId: String,
        chainId: String,
        depositType: String?,
        bondAddress: String?,
        poolId: String? = null,
    ) {
        this.vaultId = vaultId
        val chain = chainId.let(Chain::fromRaw)
        this.chain = chain
        this.depositTypeAction = depositType
        this.bondAddress = bondAddress
        this.lpPoolId = poolId

        collectAmountChanges()

        val depositOptions =
            when (chain) {
                Chain.ThorChain ->
                    listOf(
                        DepositOption.Bond,
                        DepositOption.Unbond,
                        DepositOption.Leave,
                        DepositOption.Custom,
                        DepositOption.Merge,
                        DepositOption.UnMerge,
                        DepositOption.WithdrawSecuredAsset,
                    )

                Chain.MayaChain -> listOf(DepositOption.Leave, DepositOption.Custom)

                Chain.Kujira,
                Chain.Osmosis -> listOf(DepositOption.TransferIbc)

                Chain.GaiaChain -> listOf(DepositOption.TransferIbc, DepositOption.Switch)
                Chain.Ton -> {
                    listOf(DepositOption.Stake, DepositOption.Unstake)
                }
                else ->
                    buildList {
                        //                    add(DepositOption.Stake)
                        //                    add(DepositOption.Unstake)
                        if (chain.ticker() in SECURE_ASSETS_TICKERS) add(DepositOption.SecuredAsset)
                    }
            }
        val depositOption = depositOptions.first()
        val defaultToken =
            when (chain) {
                Chain.MayaChain -> Coins.MayaChain.CACAO
                else -> Coins.ThorChain.RUNE
            }
        _state.update {
            it.copy(
                depositMessage = R.string.deposit_message_deposit_title.asUiText(chain.raw),
                depositOptions = depositOptions,
                depositOption = depositOption,
                depositChain = chain,
                selectedToken = defaultToken,
            )
        }

        val coinList =
            tokensToMerge.let {
                if (chain == Chain.Osmosis) it.filter { it.ticker.equals("LVN", ignoreCase = true) }
                else it
            }
        _state.update {
            it.copy(
                selectedCoin = coinList.first(),
                coinList = coinList,
                selectedUnMergeCoin = coinList.first(),
            )
        }

        loadAddress(vaultId, chain)

        address
            .filterNotNull()
            .onEach { address ->
                val selectedToken = address.accounts.find { it.token.isNativeToken }?.token
                selectedToken?.let { _state.update { it.copy(selectedToken = selectedToken) } }
                if (depositTypeAction == DeFiNavActions.ADD_LP.type) {
                    loadGasFeeForDisplay(address)
                }
            }
            .launchIn(viewModelScope)

        viewModelScope.launch {
            combine(
                    state.map { it.selectedCoin }.distinctUntilChanged(),
                    address.filterNotNull(),
                    state.map { it.depositOption }.distinctUntilChanged(),
                    state.map { it.selectedToken }.distinctUntilChanged(),
                ) { selectedMergeToken, address, depositOption, selectedToken ->
                    var targetTicker: String?

                    val account =
                        when (depositOption) {
                            DepositOption.Switch,
                            DepositOption.TransferIbc,
                            DepositOption.Merge -> {
                                targetTicker = selectedMergeToken.ticker
                                address.accounts.find {
                                    it.token.ticker.equals(
                                        selectedMergeToken.ticker,
                                        ignoreCase = true,
                                    )
                                }
                            }

                            DepositOption.Custom -> {
                                targetTicker = selectedToken.ticker
                                address.accounts.find { it.token.id == selectedToken.id }
                            }

                            else -> {
                                val account = address.accounts.find { it.token.isNativeToken }
                                targetTicker = account?.token?.ticker
                                account
                            }
                        }

                    if (depositOption != DepositOption.RemoveLiquidity) {
                        updateTokenAmount(account, chain, targetTicker, vaultId)
                    }

                    depositOption
                }
                .collect { depositOption ->
                    // Populate the user's own THORChain address for the SecuredAsset form here,
                    // outside the (pure) transform, and never resolve the inbound vault as a side
                    // effect — that is done synchronously in SecuredAssetStrategy so the
                    // destination always matches the currently-selected asset's chain.
                    if (depositOption == DepositOption.SecuredAsset) {
                        collectSecuredAssetAddresses()
                    }
                    setMetadataInfo()
                }
        }

        viewModelScope.launch {
            combine(
                    state.map { it.selectedCoin }.distinctUntilChanged(),
                    state.map { it.depositOption }.distinctUntilChanged(),
                ) { selectedMergeToken, depositOption ->
                    when (depositOption) {
                        DepositOption.TransferIbc,
                        DepositOption.Switch -> {
                            // special case, because of all supported merge tokens only lvn is
                            // osmosis native
                            val dstChainList =
                                if (selectedMergeToken.ticker == "LVN") {
                                    when (chain) {
                                        Chain.Osmosis -> listOf(Chain.GaiaChain)
                                        else -> listOf(Chain.Osmosis)
                                    }
                                } else {
                                    listOf(
                                            Chain.GaiaChain,
                                            Chain.Kujira,
                                            Chain.Osmosis,
                                            Chain.Noble,
                                            Chain.Akash,
                                        )
                                        .filter { it != chain }
                                }

                            _state.update { it.copy(dstChainList = dstChainList) }

                            selectDstChain(dstChainList.first())
                        }

                        else -> Unit
                    }
                }
                .collect {}
        }
    }

    private fun setMetadataInfo() {
        val action = depositTypeAction?.takeIf { it.isNotEmpty() } ?: return
        depositTypeAction = null

        val depositOption =
            when (parseDepositType(action)) {
                DeFiNavActions.BOND -> DepositOption.Bond
                DeFiNavActions.UNBOND -> DepositOption.Unbond
                DeFiNavActions.STAKE_CACAO -> DepositOption.AddCacaoPool
                DeFiNavActions.UNSTAKE_CACAO -> DepositOption.RemoveCacaoPool
                DeFiNavActions.ADD_LP -> DepositOption.AddLiquidity
                DeFiNavActions.REMOVE_LP -> DepositOption.RemoveLiquidity
                else -> DepositOption.Bond
            }
        selectDepositOption(depositOption)
    }

    private fun loadMayaBondableAssets() {
        _state.update {
            it.copy(
                bondableAssets = emptyList(),
                selectedBondAsset = "",
                availableLpUnits = null,
                removeLpUnitsDivisor = BigInteger.ZERO,
                removeLpPoolDepth = BigInteger.ZERO,
            )
        }
        assetsFieldState.clearText()
        viewModelScope.safeLaunch {
            val userAddress =
                withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                    ?.address
                    ?: run {
                        _state.update {
                            it.copy(
                                errorText =
                                    UiText.StringResource(R.string.dialog_default_error_body)
                            )
                        }
                        return@safeLaunch
                    }
            val poolMap =
                withContext(Dispatchers.IO) {
                    mayachainBondRepository.getLpBondableAssetsWithUnits(userAddress)
                }
            lpBondPoolMap = poolMap
            val assets = poolMap.keys.toList()
            val firstAsset = assets.firstOrNull() ?: ""
            val firstPool = poolMap[firstAsset]
            _state.update {
                it.copy(
                    bondableAssets = assets,
                    selectedBondAsset = firstAsset,
                    availableLpUnits = firstPool?.availableUnits,
                    removeLpUnitsDivisor =
                        firstPool?.totalPoolLpUnits?.toBigInteger() ?: BigInteger.ZERO,
                    removeLpPoolDepth = firstPool?.poolCacaoDepth?.toBigInteger() ?: BigInteger.ZERO,
                )
            }
            if (firstAsset.isNotEmpty()) {
                assetsFieldState.setTextAndPlaceCursorAtEnd(firstAsset)
            }
        }
    }

    private fun loadRemoveLpData() {
        val poolId =
            lpPoolId
                ?: run {
                    _state.update {
                        it.copy(
                            availableLpUnits = null,
                            removeLpUnitsDivisor = BigInteger.ZERO,
                            removeLpPoolDepth = BigInteger.ZERO,
                            errorText = UiText.StringResource(R.string.dialog_default_error_body),
                        )
                    }
                    return
                }
        _state.update {
            it.copy(
                availableLpUnits = null,
                removeLpUnitsDivisor = BigInteger.ZERO,
                removeLpPoolDepth = BigInteger.ZERO,
                removeLpPercent = 0f,
                removeLpCacaoDisplay = "",
                balance = R.string.share_balance_loading.asUiText(),
                errorText = null,
            )
        }
        loadLpJob?.cancel()
        loadLpJob =
            viewModelScope.safeLaunch {
                val userAddress =
                    withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                        ?.address
                        ?: run {
                            _state.update {
                                it.copy(
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body)
                                )
                            }
                            return@safeLaunch
                        }
                val memberDetails =
                    withContext(Dispatchers.IO) {
                        mayachainBondRepository.getMemberDetails(userAddress)
                    }
                val userLpUnits =
                    memberDetails.pools.find { it.pool == poolId }?.liquidityUnits
                        ?: run {
                            _state.update {
                                it.copy(
                                    availableLpUnits = null,
                                    removeLpUnitsDivisor = BigInteger.ZERO,
                                    removeLpPoolDepth = BigInteger.ZERO,
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body),
                                )
                            }
                            return@safeLaunch
                        }
                val poolStats =
                    withContext(Dispatchers.IO) { mayachainBondRepository.getLpPoolStats() }
                val pool =
                    poolStats.find { it.asset == poolId }
                        ?: run {
                            _state.update {
                                it.copy(
                                    availableLpUnits = null,
                                    removeLpUnitsDivisor = BigInteger.ZERO,
                                    removeLpPoolDepth = BigInteger.ZERO,
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body),
                                )
                            }
                            return@safeLaunch
                        }
                val totalPoolUnits = pool.units.toBigIntegerOrNull() ?: BigInteger.ZERO
                val cacaoDepth = pool.cacaoDepth.toBigIntegerOrNull() ?: BigInteger.ZERO
                val userAvailableUnits = userLpUnits.toBigIntegerOrNull()
                val userCacao =
                    if (userAvailableUnits != null) {
                        RemoveLpCalculator.computeAmountDisplay(
                            selectedUnits = userAvailableUnits,
                            poolDepth = cacaoDepth,
                            totalPoolUnits = totalPoolUnits,
                            decimals = RemoveLpCalculator.CACAO_DECIMALS,
                        )
                    } else null
                val balanceText =
                    if (userCacao != null) {
                        UiText.FormattedText(
                            R.string.remove_pool_amount_format,
                            listOf(userCacao, "CACAO"),
                        )
                    } else UiText.Empty
                _state.update {
                    it.copy(
                        availableLpUnits = userLpUnits,
                        removeLpUnitsDivisor = totalPoolUnits,
                        removeLpPoolDepth = cacaoDepth,
                        removeLpDecimals = RemoveLpCalculator.CACAO_DECIMALS,
                        removeLpTokenSymbol = "CACAO",
                        balance = balanceText,
                    )
                }
                setRemoveLpPercent(state.value.removeLpPercent)
            }
    }

    private fun loadThorChainRemoveLpData() {
        val poolId =
            lpPoolId
                ?: run {
                    _state.update {
                        it.copy(
                            availableLpUnits = null,
                            removeLpUnitsDivisor = BigInteger.ZERO,
                            removeLpPoolDepth = BigInteger.ZERO,
                            removeLpDecimals = RemoveLpCalculator.RUNE_DECIMALS,
                            removeLpTokenSymbol = Coins.ThorChain.RUNE.ticker,
                            errorText = UiText.StringResource(R.string.dialog_default_error_body),
                        )
                    }
                    return
                }
        _state.update {
            it.copy(
                availableLpUnits = null,
                removeLpUnitsDivisor = BigInteger.ZERO,
                removeLpPoolDepth = BigInteger.ZERO,
                removeLpDecimals = RemoveLpCalculator.RUNE_DECIMALS,
                removeLpTokenSymbol = Coins.ThorChain.RUNE.ticker,
                removeLpPercent = 0f,
                removeLpCacaoDisplay = "",
                balance = R.string.share_balance_loading.asUiText(),
                errorText = null,
            )
        }
        loadLpJob?.cancel()
        loadLpJob =
            viewModelScope.safeLaunch {
                val userAddress =
                    withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                        ?.address
                        ?: run {
                            _state.update {
                                it.copy(
                                    errorText =
                                        UiText.StringResource(R.string.dialog_default_error_body)
                                )
                            }
                            return@safeLaunch
                        }
                val currentVaultId = vaultId
                val pairedAddress =
                    if (currentVaultId != null) {
                        resolvePairedAddress(Chain.ThorChain, currentVaultId, poolId)
                    } else null
                val position =
                    withContext(Dispatchers.IO) {
                        getThorChainLpPositionUseCase(
                            poolId = poolId,
                            runeAddress = userAddress,
                            assetAddress = pairedAddress,
                        )
                    }

                if (position == null || position.units <= BigInteger.ZERO) {
                    _state.update {
                        it.copy(
                            availableLpUnits = null,
                            removeLpUnitsDivisor = BigInteger.ZERO,
                            removeLpPoolDepth = BigInteger.ZERO,
                            balance = UiText.Empty,
                            errorText = UiText.StringResource(R.string.dialog_default_error_body),
                        )
                    }
                    return@safeLaunch
                }

                // Use the pre-computed redeem value from the use case as `poolDepth` and the user's
                // own
                // units as `totalPoolUnits`. With selectedUnits = percent * userUnits, the
                // calculator
                // produces percent * runeRedeemValue, which is the symmetric RUNE half of
                // withdrawal.
                // Keep BigInteger end-to-end for whale positions whose units exceed Long.MAX_VALUE.
                val userUnits = position.units
                val runeRedeemBase = position.runeRedeemValue
                val symbol = Coins.ThorChain.RUNE.ticker
                val userRune =
                    RemoveLpCalculator.computeAmountDisplay(
                        selectedUnits = userUnits,
                        poolDepth = runeRedeemBase,
                        totalPoolUnits = userUnits,
                        decimals = RemoveLpCalculator.RUNE_DECIMALS,
                    )
                val balanceText =
                    if (userRune != null) {
                        UiText.FormattedText(
                            R.string.remove_pool_amount_format,
                            listOf(userRune, symbol),
                        )
                    } else UiText.Empty
                _state.update {
                    it.copy(
                        availableLpUnits = userUnits.toString(),
                        removeLpUnitsDivisor = userUnits,
                        removeLpPoolDepth = runeRedeemBase,
                        removeLpDecimals = RemoveLpCalculator.RUNE_DECIMALS,
                        removeLpTokenSymbol = symbol,
                        balance = balanceText,
                    )
                }
                setRemoveLpPercent(state.value.removeLpPercent)
            }
    }

    fun selectBondAsset(asset: String) {
        val pool = lpBondPoolMap[asset]
        _state.update {
            it.copy(
                selectedBondAsset = asset,
                availableLpUnits = pool?.availableUnits,
                removeLpUnitsDivisor = pool?.totalPoolLpUnits?.toBigInteger() ?: BigInteger.ZERO,
                removeLpPoolDepth = pool?.poolCacaoDepth?.toBigInteger() ?: BigInteger.ZERO,
                lpUnitsError = null,
            )
        }
        lpUnitsFieldState.clearText()
        assetsFieldState.setTextAndPlaceCursorAtEnd(asset)
    }

    fun setMaxLpUnits() {
        val units = state.value.availableLpUnits ?: return
        lpUnitsFieldState.setTextAndPlaceCursorAtEnd(units)
    }

    fun setRemoveLpPercent(percent: Float) {
        val s = state.value
        val availableUnits = s.availableLpUnits?.toBigIntegerOrNull() ?: return
        // Keep the slider→units math fully in BigInteger so whale positions whose units exceed
        // Long.MAX_VALUE still move the slider and compute exact withdrawal amounts. `percent` is a
        // 0f..1f fraction; convert it to integer basis points (0..10000) to retain sub-percent
        // precision, then `units * bps / 10000`.
        val basisPoints = (percent * 10_000).toInt().coerceIn(0, 10_000)
        val selectedUnits =
            availableUnits.multiply(basisPoints.toBigInteger()).divide(BigInteger.valueOf(10_000L))
        val cacaoDisplay =
            RemoveLpCalculator.computeAmountDisplay(
                selectedUnits = selectedUnits,
                poolDepth = s.removeLpPoolDepth,
                totalPoolUnits = s.removeLpUnitsDivisor,
                decimals = s.removeLpDecimals,
            ) ?: return
        lpUnitsFieldState.setTextAndPlaceCursorAtEnd(selectedUnits.toString())
        _state.update {
            it.copy(
                removeLpPercent = percent,
                removeLpBasisPoints = basisPoints,
                removeLpCacaoDisplay = cacaoDisplay,
            )
        }
    }

    private suspend fun updateTokenAmount(
        account: Account?,
        chain: Chain,
        targetTicker: String?,
        vaultId: String,
    ) {
        if (account != null) {
            val tokenValue = account.tokenValue
            if (tokenValue != null) {
                val value = mapTokenValueToStringWithUnit(tokenValue)
                _state.update { state ->
                    state.copy(
                        amountError = null,
                        balance = value.asUiText(),
                        balanceDecimal = tokenValue.decimal,
                    )
                }
            } else {
                // Account exists in vault but balance not yet loaded — clear stale error and
                // balance
                _state.update {
                    it.copy(amountError = null, balance = UiText.Empty, balanceDecimal = null)
                }
            }
        } else {
            _state.update {
                it.copy(
                    balance = UiText.Empty,
                    balanceDecimal = null,
                    amountError =
                        UiText.FormattedText(
                            R.string.must_be_enabled_before_proceeding,
                            listOf(targetTicker.orEmpty()),
                        ),
                )
            }
        }
    }

    private fun loadGasFeeForDisplay(address: Address) {
        val chain = chain ?: return
        viewModelScope.safeLaunch {
            val token = address.accounts.find { it.token.isNativeToken }?.token ?: return@safeLaunch
            val srcAddress = token.address
            val gasFee = calculateGasFee(chain, token, srcAddress)
            val specific =
                blockChainSpecificRepository.getSpecific(
                    chain,
                    srcAddress,
                    token,
                    gasFee,
                    isSwap = false,
                    isMaxAmountEnabled = false,
                    isDeposit = true,
                )
            val estimatedGasFee = getFeesFiatValue(specific, gasFee, token)
            _state.update {
                it.copy(
                    totalGas = UiText.DynamicString(estimatedGasFee.formattedTokenValue),
                    estimatedFee = UiText.DynamicString(estimatedGasFee.formattedFiatValue),
                )
            }
        }
    }

    private fun loadAddress(vaultId: String, chain: Chain) {
        addressJob?.cancel()
        addressJob =
            viewModelScope.launch {
                try {
                    accountsRepository.loadAddress(vaultId, chain).collect { address ->
                        this@DepositFormViewModel.address.value = address
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e)
                }
            }
    }

    fun selectToken() {
        val chain = chain ?: return

        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val requestId = UUID.randomUUID().toString()

            navigator.route(
                Route.SelectAsset(
                    requestId = requestId,
                    vaultId = vaultId,
                    preselectedNetworkId = chain.id,
                    networkFilters = Route.SelectNetwork.Filters.DisableNetworkSelection,
                )
            )
            val selectedAsset = requestResultRepository.request<AssetSelected?>(requestId)
            val selectedToken = selectedAsset?.token

            if (selectedToken != null) {
                _state.update { it.copy(selectedToken = selectedToken) }
            }
        }
    }

    fun selectDepositOption(option: DepositOption) {
        // Stop any in-flight Remove LP fetch so it can't write stale state into the new option.
        loadLpJob?.cancel()
        // Stop any in-flight Switch inbound fetch so a late callback can't overwrite the
        // freshly reset dstAddressError or keep writing to thorAddressFieldState from a stale
        // Switch context.
        switchInboundJob?.cancel()
        // Stop the previous WithdrawSecuredAsset address collector so re-selecting the option does
        // not leak an additional permanent collector running handleWithdrawSecuredAsset.
        withdrawSecuredAssetJob?.cancel()
        viewModelScope.launch {
            resetTextFields()
            _state.update { it.copy(depositOption = option) }

            when (option) {
                DepositOption.Switch -> {
                    switchInboundJob =
                        viewModelScope.launch {
                            val vaultId = vaultId ?: return@launch
                            try {
                                when (
                                    val result = fetchThorChainInboundForChain(SWITCH_INBOUND_CHAIN)
                                ) {
                                    is InboundAddressResult.Available -> {
                                        nodeAddressFieldState.setTextAndPlaceCursorAtEnd(
                                            result.address
                                        )
                                        _state.update { it.copy(dstAddressError = null) }
                                    }
                                    InboundAddressResult.Halted ->
                                        _state.update {
                                            it.copy(
                                                dstAddressError =
                                                    UiText.FormattedText(
                                                        R.string
                                                            .deposit_error_thorchain_chain_halted,
                                                        listOf(Chain.GaiaChain.raw),
                                                    )
                                            )
                                        }
                                    InboundAddressResult.FetchFailed,
                                    InboundAddressResult.Unsupported ->
                                        _state.update {
                                            it.copy(
                                                dstAddressError =
                                                    UiText.StringResource(
                                                        R.string
                                                            .deposit_error_thorchain_inbound_unavailable
                                                    )
                                            )
                                        }
                                }
                                accountsRepository.loadAddress(vaultId, Chain.ThorChain).collect {
                                    addresses ->
                                    thorAddressFieldState.setTextAndPlaceCursorAtEnd(
                                        addresses.address
                                    )
                                }
                            } catch (e: Exception) {
                                if (e is kotlinx.coroutines.CancellationException) throw e
                                Timber.e(e)
                            }
                        }
                }

                DepositOption.Bond,
                DepositOption.Unbond -> {
                    val defaultBondToken =
                        if (chain == Chain.MayaChain) Coins.MayaChain.CACAO
                        else Coins.ThorChain.RUNE
                    _state.update {
                        it.copy(selectedToken = defaultBondToken, unstakableAmount = null)
                    }
                    if (chain == Chain.MayaChain) {
                        loadMayaBondableAssets()
                    }
                }

                DepositOption.Leave -> {
                    val leaveToken =
                        if (chain == Chain.MayaChain) Coins.MayaChain.CACAO
                        else Coins.ThorChain.RUNE
                    _state.update { it.copy(selectedToken = leaveToken, unstakableAmount = null) }
                }

                DepositOption.RemoveCacaoPool -> {
                    handleRemoveCacaoOption()
                }

                DepositOption.AddLiquidity -> {
                    val token =
                        if (chain == Chain.MayaChain) Coins.MayaChain.CACAO
                        else Coins.ThorChain.RUNE
                    _state.update { it.copy(selectedToken = token, unstakableAmount = null) }
                }

                DepositOption.RemoveLiquidity -> {
                    val token =
                        if (chain == Chain.MayaChain) Coins.MayaChain.CACAO
                        else Coins.ThorChain.RUNE
                    _state.update { it.copy(selectedToken = token, unstakableAmount = null) }
                    when (chain) {
                        Chain.MayaChain -> loadRemoveLpData()
                        Chain.ThorChain -> loadThorChainRemoveLpData()
                        else -> Unit
                    }
                }

                DepositOption.WithdrawSecuredAsset -> {
                    withdrawSecuredAssetJob =
                        viewModelScope.launch {
                            this@DepositFormViewModel.address.filterNotNull().collect { address ->
                                handleWithdrawSecuredAsset(address)
                            }
                        }
                }

                else -> Unit
            }

            if (!bondAddress.isNullOrEmpty()) {
                nodeAddressFieldState.setTextAndPlaceCursorAtEnd(bondAddress!!)
            }
        }
    }

    private fun handleWithdrawSecuredAsset(address: Address) {
        thorAddressFieldState.setTextAndPlaceCursorAtEnd(address.address)
        val availableSecuredAssets =
            address.accounts
                .filter { account -> account.token.isSecuredAsset() }
                .map {
                    TokenWithdrawSecureAsset(
                        ticker = it.token.ticker,
                        contract = it.token.contractAddress,
                        coin = it.token,
                        tokenValue = it.tokenValue,
                    )
                }
        val selectedSecuredAsset = availableSecuredAssets.firstOrNull()
        val balance = selectedSecuredAsset?.tokenValue?.let(mapTokenValueToStringWithUnit)
        _state.update {
            it.copy(
                availableSecuredAssets = availableSecuredAssets,
                securedAssetsLoaded = true,
                selectedSecuredAsset = selectedSecuredAsset ?: TokenWithdrawSecureAsset.EMPTY,
                balance = balance?.asUiText() ?: UiText.Empty,
            )
        }
    }

    private fun collectSecuredAssetAddresses() {
        securedAssetThorAddressJob?.cancel()
        securedAssetThorAddressJob =
            viewModelScope.safeLaunch(
                onError = { Timber.e(it, "Failed to collect secured asset addresses") }
            ) {
                val vaultId = vaultId ?: return@safeLaunch
                val vault =
                    vaultRepository.get(vaultId)
                        ?: run {
                            return@safeLaunch
                        }
                val (thorAddress) =
                    chainAccountAddressRepository.getAddress(chain = Chain.ThorChain, vault = vault)

                thorAddressFieldState.setTextAndPlaceCursorAtEnd(thorAddress)
            }
    }

    private suspend fun fetchSecuredAssetInboundAddress(): InboundAddressResult {
        val chainName = state.value.selectedToken.getChainName()
        return fetchThorChainInboundForChain(chainName)
    }

    /**
     * Resolves the THORChain inbound vault address for a secured-asset deposit of [selectedToken],
     * translating halt/unsupported/fetch-failure outcomes into user-facing errors.
     *
     * @param selectedToken the UTXO/asset token being deposited.
     * @return the inbound vault address to deposit to.
     */
    private suspend fun requireSecuredAssetInboundAddress(selectedToken: Coin): String =
        when (val result = fetchSecuredAssetInboundAddress()) {
            is InboundAddressResult.Available -> result.address
            InboundAddressResult.Halted ->
                throw InvalidTransactionDataException(
                    UiText.FormattedText(
                        R.string.deposit_error_thorchain_chain_halted,
                        listOf(selectedToken.getChainName()),
                    )
                )
            InboundAddressResult.Unsupported ->
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_error_not_secured_asset)
                )
            InboundAddressResult.FetchFailed ->
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.deposit_error_thorchain_inbound_unavailable)
                )
        }

    /**
     * Fetches THORChain's inbound vault address for [chainName] (matched against the THORChain
     * inbound addresses endpoint, case-insensitive) and reports halt/network failure modes so
     * callers can surface a distinct user error instead of silently leaving the destination empty.
     */
    private suspend fun fetchThorChainInboundForChain(chainName: String): InboundAddressResult =
        try {
            val inboundAddresses = thorChainApi.getTHORChainInboundAddresses()
            val inboundAddress =
                inboundAddresses.firstOrNull { it.chain.equals(chainName, ignoreCase = true) }
            when {
                inboundAddress == null -> InboundAddressResult.Unsupported
                inboundAddress.halted ||
                    inboundAddress.chainTradingPaused ||
                    inboundAddress.chainLPActionsPaused ||
                    inboundAddress.globalTradingPaused -> InboundAddressResult.Halted
                else -> InboundAddressResult.Available(inboundAddress.address)
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to fetch THORChain inbound for %s", chainName)
            InboundAddressResult.FetchFailed
        }

    private sealed class InboundAddressResult {
        data class Available(val address: String) : InboundAddressResult()

        data object Halted : InboundAddressResult()

        data object Unsupported : InboundAddressResult()

        data object FetchFailed : InboundAddressResult()
    }

    private suspend fun handleRemoveCacaoOption() {
        val addressValue = address.value?.address ?: return
        loadCacaoMaturityStatus(addressValue)
        try {
            val balance = mayaChainApi.getUnStakeCacaoBalance(addressValue)
            balance?.let {
                val balanceInt = it.toBigIntegerOrNull()
                if (balanceInt == null) {
                    Timber.e("Invalid balance format: $it")
                    _state.update { state -> state.copy(unstakableAmount = null) }
                    return
                }
                val unstakableAmount =
                    mapTokenValueToStringWithUnit(
                        TokenValue(value = balanceInt, token = Coins.MayaChain.CACAO)
                    )
                _state.update { state -> state.copy(unstakableAmount = unstakableAmount) }
            } ?: run { _state.update { state -> state.copy(unstakableAmount = null) } }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Timber.e(e, "Failed to fetch unstakable CACAO balance")
            _state.update { state ->
                state.copy(
                    unstakableAmount = null,
                    errorText = UiText.StringResource(R.string.dialog_default_error_body),
                )
            }
        }
    }

    private fun loadCacaoMaturityStatus(addressValue: String) {
        cacaoMaturityJob?.cancel()
        cacaoMaturityJob =
            viewModelScope.safeLaunch {
                val status = getMayaCacaoMaturityStatus(addressValue)
                _state.update { state ->
                    state.copy(
                        isUnstakeMature = status.isMature,
                        unstakeUnlocksInText = status.toUnlocksInText(),
                    )
                }
            }
    }

    private fun MayaCacaoMaturityStatus.toUnlocksInText(): UiText? =
        when {
            isUnknown -> UiText.StringResource(R.string.unstake_cacao_maturity_check_failed)
            isMature || remainingSeconds <= 0L -> null
            else -> cacaoUnlocksInUiText(remainingSeconds)
        }

    fun selectDstChain(chain: Chain) {
        nodeAddressFieldState.clearText()

        _state.update { it.copy(selectedDstChain = chain, dstAddressError = null) }

        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val address = accountsRepository.loadAddress(vaultId, chain).firstOrNull()

            if (address != null) {
                nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address.address)
            }
        }
    }

    fun selectMergeToken(mergeInfo: TokenMergeInfo) {
        _state.update { it.copy(selectedCoin = mergeInfo) }
    }

    fun selectUnMergeToken(unmergeInfo: TokenMergeInfo) {
        _state.update { it.copy(selectedUnMergeCoin = unmergeInfo) }
        if (rujiMergeBalances.value == null) {
            onLoadRujiMergeBalances()
        } else {
            setUnMergeTokenSharesField(unmergeInfo)
        }
    }

    private fun resetTextFields() {
        tokenAmountFieldState.clearText()
        nodeAddressFieldState.clearText()
        providerFieldState.clearText()
        operatorFeeFieldState.clearText()
        customMemoFieldState.clearText()
        basisPointsFieldState.clearText()
        lpUnitsFieldState.clearText()
        assetsFieldState.clearText()
        rewardsAmountFieldState.clearText()
        _state.update {
            it.copy(
                tokenAmountError = null,
                nodeAddressError = null,
                dstAddressError = null,
                thorAddressError = null,
            )
        }
    }

    /**
     * Validates the destination address shown on the IBC Transfer and Switch sub-forms against the
     * appropriate chain (selected destination chain for IBC, source/Gaia chain for Switch),
     * surfacing inline errors via [DepositFormUiModel.dstAddressError]. Other deposit options leave
     * the field error untouched.
     */
    fun validateDstAddress() {
        val depositOption = state.value.depositOption
        val validationChain =
            when (depositOption) {
                DepositOption.TransferIbc -> state.value.selectedDstChain
                DepositOption.Switch -> chain
                else -> return
            }
        val dstAddress = nodeAddressFieldState.text.toString()
        // For Switch the dst field is auto-populated from the THORChain inbound vault. When the
        // fetch returns halt/unavailable, the field is left blank and dstAddressError carries the
        // actionable reason; running the generic blank-check here would clobber that context.
        // Only skip when dstAddressError is already set (halt/unavailable) — if it's null the user
        // manually cleared the field in the healthy path, so we must validate and surface the blank
        // error to block Continue.
        if (
            depositOption == DepositOption.Switch &&
                dstAddress.isBlank() &&
                state.value.dstAddressError != null
        )
            return
        val error = dstAddressErrorOrNull(validationChain, dstAddress)
        _state.update { it.copy(dstAddressError = error) }
    }

    fun validateNodeAddress() {
        val nodeAddress = nodeAddressFieldState.text.toString()
        val errorText = addressErrorOrNull(chain, nodeAddress)
        if (errorText != null) {
            whitelistJob?.cancel()
            _state.update { it.copy(nodeAddressError = errorText, isCheckingWhitelist = false) }
            return
        }
        if (chain == Chain.MayaChain && state.value.depositOption == DepositOption.Bond) {
            whitelistJob?.cancel()
            _state.update {
                it.copy(
                    nodeAddressError = null,
                    isCheckingWhitelist = true,
                    isWhitelistFailed = false,
                )
            }
            whitelistJob = viewModelScope.safeLaunch { checkNodeWhitelist(nodeAddress) }
        } else {
            _state.update { it.copy(nodeAddressError = null) }
        }
    }

    private suspend fun checkNodeWhitelist(nodeAddress: String) {
        try {
            val userAddress =
                withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                    ?.address
                    ?: run {
                        _state.update { it.copy(isCheckingWhitelist = false) }
                        return
                    }
            val nodeInfo = mayachainBondRepository.getNodeDetails(nodeAddress)
            if (
                nodeAddressFieldState.text.toString() != nodeAddress ||
                    chain != Chain.MayaChain ||
                    state.value.depositOption != DepositOption.Bond
            ) {
                _state.update { it.copy(isCheckingWhitelist = false) }
                return
            }
            val isWhitelisted =
                nodeInfo.bondProviders.providers.any { it.bondAddress == userAddress }
            if (!isWhitelisted) {
                _state.update {
                    it.copy(
                        nodeAddressError =
                            UiText.StringResource(R.string.bond_not_whitelisted_error),
                        isCheckingWhitelist = false,
                        isWhitelistFailed = true,
                    )
                }
            } else {
                _state.update {
                    it.copy(
                        nodeAddressError = null,
                        isCheckingWhitelist = false,
                        isWhitelistFailed = false,
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.w(e, "Whitelist check failed for node %s", nodeAddress)
            _state.update {
                it.copy(
                    nodeAddressError = UiText.StringResource(R.string.dialog_default_error_body),
                    isCheckingWhitelist = false,
                    isWhitelistFailed = true,
                )
            }
        }
    }

    fun validateTokenAmount() {
        val errorText = validateTokenAmount(tokenAmountFieldState.text.toString())
        _state.update { it.copy(tokenAmountError = errorText) }
    }

    fun validateAndDeposit() {
        validateTokenAmount()
        if (state.value.tokenAmountError == null) {
            deposit()
        }
    }

    fun validateProvider() {
        val errorText = addressErrorOrNull(chain, providerFieldState.text.toString())
        _state.update { it.copy(providerError = errorText) }
    }

    fun validateOperatorFee() {
        val text = operatorFeeFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = validateBasisPoints(text.toIntOrNull())
            _state.update { it.copy(operatorFeeError = errorText) }
        }
    }

    fun validateCustomMemo() {
        val errorText = validateCustomMemo(customMemoFieldState.text.toString())
        _state.update { it.copy(customMemoError = errorText) }
    }

    fun validateBasisPoints() {
        val text = basisPointsFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = validateBasisPoints(text.toIntOrNull())
            _state.update { it.copy(basisPointsError = errorText) }
        }
    }

    fun validateSlippage() {
        val text = slippageFieldState.text.toString()
        val errorText = validateSlippage(text)
        _state.update { it.copy(slippageError = errorText) }
    }

    private fun validateSlippage(slippage: String?): UiText? {
        if (slippage.isNullOrBlank()) {
            return UiText.StringResource(R.string.slippage_required_error)
        }

        return try {
            val value = slippage.toBigDecimal()
            if (value < BigDecimal.ZERO || value > BigDecimal("100")) {
                UiText.StringResource(R.string.slippage_invalid_error)
            } else {
                null
            }
        } catch (e: NumberFormatException) {
            UiText.StringResource(R.string.slippage_format_error)
        }
    }

    fun setProvider(provider: String) {
        providerFieldState.setTextAndPlaceCursorAtEnd(provider)
    }

    fun setNodeAddress(address: String) {
        nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateNodeAddress()
    }

    /** Sets the destination address on the IBC Transfer / Switch sub-forms and revalidates. */
    fun setDstAddress(address: String) {
        nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateDstAddress()
    }

    /**
     * Validates the destination THORChain address on the Switch sub-form against ThorChain,
     * surfacing inline errors via [DepositFormUiModel.thorAddressError]. No-op outside the Switch
     * flow so SECURE+ auto-populated values do not trigger inline errors.
     */
    fun validateThorAddress() {
        if (state.value.depositOption != DepositOption.Switch) return
        val errorText = addressErrorOrNull(Chain.ThorChain, thorAddressFieldState.text.toString())
        _state.update { it.copy(thorAddressError = errorText) }
    }

    /** Sets the THORChain destination address on the Switch sub-form and revalidates. */
    fun setThorAddress(address: String) {
        thorAddressFieldState.setTextAndPlaceCursorAtEnd(address)
        validateThorAddress()
    }

    private fun setSlippage(slippage: String) {
        slippageFieldState.setTextAndPlaceCursorAtEnd(slippage)
    }

    fun scan() {
        viewModelScope.launch {
            val qr = requestQrScan()
            if (!qr.isNullOrBlank()) {
                setNodeAddress(qr)
            }
        }
    }

    fun openAddressBook() {
        viewModelScope.launch {
            val vaultId = vaultId ?: return@launch
            val chainId = chain?.id ?: return@launch
            val address: AddressBookEntry =
                requestAddressBookEntry(chainId = chainId, excludeVaultId = vaultId)
                    ?: return@launch
            setNodeAddress(address.address)
        }
    }

    fun dismissError() {
        _state.update { it.copy(errorText = null) }
    }

    fun deposit() {
        viewModelScope.launch {
            try {
                val vaultId = vaultId ?: return@launch
                isLoading = true

                val transaction = depositStrategies.getValue(state.value.depositOption).build()

                transactionRepository.addTransaction(transaction)

                sendNavigator.navigate(
                    SendDst.VerifyTransaction(transactionId = transaction.id, vaultId = vaultId)
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e)
                showError(UiText.StringResource(R.string.dialog_default_error_body))
            } finally {
                isLoading = false
            }
        }
    }

    /**
     * For symmetric LP add the memo carries the user's address on the *paired* chain so THORChain
     * can credit them when the asset half is later deposited from that chain. Returns null when the
     * pool refers to the native chain (no pair) or when the asset chain can't be resolved.
     */
    private suspend fun resolvePairedAddress(
        chain: Chain,
        vaultId: String,
        poolId: String,
    ): String? {
        if (chain != Chain.ThorChain) return null
        val parsed =
            parseThorChainPool(poolId).takeIf { it.chain != null && it.chain != Chain.ThorChain }
                ?: return null
        val assetChain = parsed.chain ?: return null
        return try {
            val vault = vaultRepository.get(vaultId) ?: return null
            chainAccountAddressRepository.getAddress(chain = assetChain, vault = vault).first
        } catch (e: Throwable) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to resolve paired address for $poolId")
            null
        }
    }

    private fun getSelectedToken(): Coin? {
        return getSelectedAccount()?.token
    }

    private fun getSelectedAccount(): Account? {
        val address = address.value ?: return null
        val userSelectedToken = state.value.selectedToken
        return address.accounts.firstOrNull { it.token.id == userSelectedToken.id }
    }

    fun onLoadRujiMergeBalances() {
        viewModelScope.launch {
            try {
                val selectedToken = state.value.selectedUnMergeCoin
                val addressString =
                    address.value?.address
                        ?: throw RuntimeException("Invalid address: cannot fetch balance")

                withContext(Dispatchers.IO) {
                    val newBalances = thorChainApi.getRujiMergeBalances(addressString)
                    rujiMergeBalances.update { newBalances }
                }

                setUnMergeTokenSharesField(selectedToken)
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                _state.update { it.copy(sharesBalance = UiText.Empty) }
                Timber.e("Can't load Ruji Balances ${t.message}")
            } finally {
                isLoading = false
            }
        }
    }

    private fun setUnMergeTokenSharesField(selectedToken: TokenMergeInfo) {
        val selectedSymbol = selectedToken.ticker
        val selectedMergeAccount =
            rujiMergeBalances.value?.firstOrNull {
                it.pool?.mergeAsset?.metadata?.symbol.equals(selectedSymbol, true)
            } ?: return

        val amountText =
            selectedMergeAccount.shares?.toBigInteger()?.let {
                CoinType.THORCHAIN.toValue(it).toString()
            } ?: "0"

        _state.update { it.copy(sharesBalance = amountText.asUiText()) }

        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amountText)
    }

    private fun requireTokenAmount(
        selectedToken: Coin,
        selectedAccount: Account,
        address: Address,
        gas: TokenValue,
    ): BigInteger {
        val tokenAmount = tokenAmountFieldState.text.toString().toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt = tokenAmount.movePointRight(selectedToken.decimal).toBigInteger()

        val nativeTokenAccount =
            address.accounts.find { it.token.isNativeToken && it.token.chain == chain }
        val nativeTokenValue =
            nativeTokenAccount?.tokenValue?.value
                ?: throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_no_token)
                )

        if (selectedToken.isNativeToken) {
            // Native-token deposits pay the amount and the gas from the same balance, so validate
            // amount + gas in-form; otherwise a full-balance amount fails late at signing.
            if (nativeTokenValue < tokenAmountInt + gas.value) {
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_insufficient_balance)
                )
            }
        } else {
            if ((selectedAccount.tokenValue?.value ?: BigInteger.ZERO) < tokenAmountInt) {

                // For all other operations, or if the unstakable check failed
                throw InvalidTransactionDataException(
                    UiText.StringResource(R.string.send_error_insufficient_balance)
                )
            }

            if (nativeTokenValue < gas.value) {
                throw InvalidTransactionDataException(
                    UiText.FormattedText(
                        R.string.insufficient_native_token,
                        listOf(nativeTokenAccount.token.ticker),
                    )
                )
            }
        }

        return tokenAmountInt
    }

    private fun showError(text: UiText) {
        _state.update { it.copy(errorText = text) }
    }

    private fun validateCustomMemo(memo: String): UiText? =
        if (memo.isBlank()) {
            UiText.StringResource(R.string.dialog_default_error_title)
        } else {
            null
        }

    /**
     * Returns a generic address-format error or `null` if [address] parses as valid for [chain].
     * Used by [validateNodeAddress], [validateProvider] and [validateThorAddress] where the field
     * label is already chain-specific in the UI, so a single "Address is invalid" message suffices.
     */
    private fun addressErrorOrNull(chain: Chain?, address: String): UiText? {
        if (chain == null) return UiText.StringResource(R.string.dialog_default_error_title)
        if (address.isBlank() || !chainAccountAddressRepository.isValid(chain, address))
            return UiText.StringResource(R.string.send_error_no_address)
        return null
    }

    /**
     * Returns a destination-address error specific to the IBC/Switch dst field, distinguishing
     * blank from invalid-format so the user sees the more actionable message. Used by both the
     * public [validateDstAddress] blur helper and the TransferIbc/Switch submit guards so the
     * inline error and the thrown error stay in lockstep.
     */
    private fun dstAddressErrorOrNull(chain: Chain?, dstAddress: String): UiText? {
        if (chain == null) return UiText.StringResource(R.string.dialog_default_error_title)
        if (dstAddress.isBlank())
            return UiText.StringResource(R.string.deposit_error_destination_address)
        if (!chainAccountAddressRepository.isValid(chain, dstAddress))
            return UiText.StringResource(R.string.deposit_error_invalid_destination_address)
        return null
    }

    private fun validateTokenAmount(tokenAmount: String): UiText? {
        if (tokenAmount.length > TextFieldUtils.AMOUNT_MAX_LENGTH)
            return UiText.StringResource(R.string.send_from_invalid_amount)
        val tokenAmountBigDecimal = tokenAmount.toBigDecimalOrNull()
        if (tokenAmountBigDecimal == null || tokenAmountBigDecimal < BigDecimal.ZERO) {
            return UiText.StringResource(R.string.send_error_no_amount)
        }
        return null
    }

    private fun validateBasisPoints(basisPoints: Int?): UiText? {
        if (basisPoints == null || basisPoints <= 0 || basisPoints > 100) {
            return UiText.StringResource(R.string.send_from_invalid_amount)
        }
        return null
    }

    fun validateAssets() {
        val assets = assetsFieldState.text.toString()
        _state.update {
            it.copy(
                assetsError =
                    if (!isAssetCharsValid(assets))
                        UiText.StringResource(R.string.deposit_error_invalid_assets)
                    else null
            )
        }
    }

    fun validateLpUnits() {
        val lpUnits = lpUnitsFieldState.text.toString()
        _state.update {
            it.copy(
                lpUnitsError =
                    if (!isLpUnitCharsValid(lpUnits))
                        UiText.StringResource(R.string.deposit_error_invalid_lpunits)
                    else null
            )
        }
    }

    private suspend fun getFeesFiatValue(
        specific: BlockChainSpecificAndUtxo,
        gasFee: TokenValue,
        selectedToken: Coin,
    ): EstimatedGasFee {
        return gasFeeToEstimatedFee(
            GasFeeParams(
                gasLimit =
                    if (chain?.standard == TokenStandard.EVM) {
                        (specific.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
                    } else {
                        BigInteger.valueOf(1)
                    },
                gasFee = gasFee,
                selectedToken = selectedToken,
            )
        )
    }

    private fun collectAmountChanges() {
        if (amountChangesJob != null) return
        amountChangesJob =
            viewModelScope.safeLaunch {
                combine(
                        state.map { it.selectedToken }.distinctUntilChanged(),
                        tokenAmountFieldState.textAsFlow(),
                        fiatAmountFieldState.textAsFlow(),
                    ) { selectedToken, tokenFieldValue, fiatFieldValue ->
                        val tokenString = tokenFieldValue.toString()
                        val fiatString = fiatFieldValue.toString()
                        if (lastTokenValueUserInput != tokenString) {
                            val fiatValue =
                                convertAmountValue(tokenString, selectedToken) { value, price ->
                                        value
                                            .multiply(price)
                                            .setScale(selectedToken.decimal, RoundingMode.DOWN)
                                            .stripTrailingZeros()
                                    }
                                    ?.takeIf { it.isNotEmpty() } ?: return@combine
                            lastTokenValueUserInput = tokenString
                            lastFiatValueUserInput = fiatValue
                            fiatAmountFieldState.setTextAndPlaceCursorAtEnd(fiatValue)
                        } else if (lastFiatValueUserInput != fiatString) {
                            val tokenValue =
                                convertAmountValue(fiatString, selectedToken) { value, price ->
                                        value.divide(
                                            price,
                                            selectedToken.decimal,
                                            RoundingMode.DOWN,
                                        )
                                    }
                                    ?.takeIf { it.isNotEmpty() } ?: return@combine
                            lastTokenValueUserInput = tokenValue
                            lastFiatValueUserInput = fiatString
                            tokenAmountFieldState.setTextAndPlaceCursorAtEnd(tokenValue)
                        }
                    }
                    .collect()
            }
    }

    private suspend fun convertAmountValue(
        value: String,
        token: Coin,
        transform: (value: BigDecimal, price: BigDecimal) -> BigDecimal,
    ): String? {
        val decimalValue = value.toBigDecimalOrNull() ?: return ""
        return try {
            val price = tokenPriceRepository.getPrice(token, appCurrency.value).first()
            if (price == BigDecimal.ZERO) {
                Timber.w(
                    "convertAmountValue: price is ZERO for token %s, skipping conversion",
                    token.ticker,
                )
                return null
            }
            transform(decimalValue, price).toPlainString()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.d(e, "Failed to get price for token %s", token.ticker)
            null
        }
    }

    private fun isLpUnitCharsValid(lpUnits: String) =
        lpUnits.toLongOrNull() != null && lpUnits.all { it.isDigit() } && lpUnits.toLong() > 0

    fun onSelectSecureAsset(asset: TokenWithdrawSecureAsset) {
        val balance = asset.tokenValue?.let(mapTokenValueToStringWithUnit)
        _state.update {
            it.copy(selectedSecuredAsset = asset, balance = balance?.asUiText() ?: UiText.Empty)
        }
    }

    @kotlin.ExperimentalStdlibApi
    private fun selectUtxosIfNeeded(
        chain: Chain,
        specific: BlockChainSpecificAndUtxo,
    ): BlockChainSpecificAndUtxo {
        specific.blockChainSpecific as? BlockChainSpecific.UTXO ?: return specific

        val updatedUtxo =
            planBtc?.utxosOrBuilderList?.map { planUtxo ->
                UtxoInfo(
                    hash = planUtxo.outPoint.hash.toByteArray().reversedArray().toHexString(),
                    index = planUtxo.outPoint.index.toUInt(),
                    amount = planUtxo.amount,
                )
            } ?: return specific

        return specific.copy(utxos = updatedUtxo)
    }

    private fun validateBtcLikeAmount(tokenAmountInt: BigInteger, chain: Chain) {
        val minAmount = chain.getDustThreshold
        if (tokenAmountInt < minAmount) {
            val symbol = chain.coinType.symbol
            val name = chain.raw
            val formattedMinAmount = chain.toValue(minAmount).toString()
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.send_form_minimum_send_amount_is_requires_this,
                    listOf(formattedMinAmount, symbol, name),
                )
            )
        }
        if (planBtc?.error != SigningError.OK) {
            throw InvalidTransactionDataException(R.string.insufficient_utxos_error.asUiText())
        }
    }

    private suspend fun calculateGasFee(chain: Chain, token: Coin, srcAddress: String): TokenValue {
        val vaultId = vaultId ?: error("Vault ID not set")
        val vault =
            withContext(Dispatchers.IO) { vaultRepository.get(vaultId) } ?: error("Vault not found")
        val blockchainTransaction =
            Transfer(
                coin = token,
                vault =
                    VaultData(
                        vaultHexChainCode = vault.hexChainCode,
                        vaultHexPublicKey = vault.getPubKeyByChain(chain),
                    ),
                amount = BigInteger.ZERO,
                to = srcAddress,
                isMax = false,
            )
        val fees =
            withContext(Dispatchers.IO) { feeServiceComposite.calculateFees(blockchainTransaction) }
        val nativeCoin = withContext(Dispatchers.IO) { tokenRepository.getNativeToken(chain.id) }
        return TokenValue(value = fees.amount, token = nativeCoin)
    }

    private suspend fun getBitcoinTransactionPlan(
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

        val plan = utxo.getBitcoinTransactionPlan(keysignPayload)
        return plan
    }

    companion object {
        private const val ADDRESS_AWAIT_TIMEOUT_MS = 5_000L

        /** THORChain inbound-addresses chain key used by the Switch (Gaia/ATOM) deposit option. */
        private const val SWITCH_INBOUND_CHAIN = "GAIA"
    }
}

internal data class TokenMergeInfo(val ticker: String, val contract: String) {

    val denom: String
        get() = "thor.$ticker".lowercase()
}

internal data class TokenWithdrawSecureAsset(
    val ticker: String,
    val contract: String,
    val coin: Coin,
    val tokenValue: TokenValue?,
) {
    companion object {
        val EMPTY =
            TokenWithdrawSecureAsset(
                ticker = "Select Asset",
                contract = "",
                coin = Coin.EMPTY,
                tokenValue = null,
            )
    }
}

private val tokensToMerge =
    listOf(
        TokenMergeInfo(
            ticker = "KUJI",
            contract = "thor14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s3p2nzy",
        ),
        TokenMergeInfo(
            ticker = "rKUJI",
            contract = "thor1yyca08xqdgvjz0psg56z67ejh9xms6l436u8y58m82npdqqhmmtqrsjrgh",
        ),
        TokenMergeInfo(
            ticker = "FUZN",
            contract = "thor1suhgf5svhu4usrurvxzlgn54ksxmn8gljarjtxqnapv8kjnp4nrsw5xx2d",
        ),
        TokenMergeInfo(
            ticker = "NSTK",
            contract = "thor1cnuw3f076wgdyahssdkd0g3nr96ckq8cwa2mh029fn5mgf2fmcmsmam5ck",
        ),
        TokenMergeInfo(
            ticker = "WINK",
            contract = "thor1yw4xvtc43me9scqfr2jr2gzvcxd3a9y4eq7gaukreugw2yd2f8tsz3392y",
        ),
        TokenMergeInfo(
            ticker = "LVN",
            contract = "thor1ltd0maxmte3xf4zshta9j5djrq9cl692ctsp9u5q0p9wss0f5lms7us4yf",
        ),
    )
