package com.vultisig.wallet.ui.models.deposit

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.api.MayaChainApi
import com.vultisig.wallet.data.api.MergeAccount
import com.vultisig.wallet.data.api.RujiStakeBalances
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.blockchain.FeeServiceComposite
import com.vultisig.wallet.data.blockchain.model.Transfer
import com.vultisig.wallet.data.blockchain.model.VaultData
import com.vultisig.wallet.data.chains.helpers.ThorchainFunctions
import com.vultisig.wallet.data.chains.helpers.UtxoHelper
import com.vultisig.wallet.data.crypto.ThorChainHelper.Companion.SECURE_ASSETS_TICKERS
import com.vultisig.wallet.data.crypto.getChainName
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.Coins
import com.vultisig.wallet.data.models.DepositMemo
import com.vultisig.wallet.data.models.DepositMemo.Bond
import com.vultisig.wallet.data.models.DepositMemo.Unbond
import com.vultisig.wallet.data.models.DepositTransaction
import com.vultisig.wallet.data.models.EstimatedGasFee
import com.vultisig.wallet.data.models.GasFeeParams
import com.vultisig.wallet.data.models.OPERATION_MINT
import com.vultisig.wallet.data.models.OPERATION_WITHDRAW
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
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BlockChainSpecificAndUtxo
import com.vultisig.wallet.data.repositories.BlockChainSpecificRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.DepositTransactionRepository
import com.vultisig.wallet.data.repositories.GasFeeRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DepositMemoAssetsValidatorUseCase
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCase
import com.vultisig.wallet.data.usecases.GasFeeToEstimatedFeeUseCaseImpl
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.data.usecases.ValidateMayaTransactionHeightUseCase
import com.vultisig.wallet.data.utils.TextFieldUtils
import com.vultisig.wallet.data.utils.getChain
import com.vultisig.wallet.data.utils.getCoinBy
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.data.utils.toUnit
import com.vultisig.wallet.data.utils.toValue
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.models.send.InvalidTransactionDataException
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.SendDst
import com.vultisig.wallet.ui.screens.select.AssetSelected
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_YTCY_AFFILIATE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_RUJI_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.STAKING_TCY_COMPOUND_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YRUNE_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.YTCY_CONTRACT
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.parseDepositType
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.asUiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import vultisig.keysign.v1.TransactionType
import vultisig.keysign.v1.WasmExecuteContractPayload
import wallet.core.jni.CoinType
import wallet.core.jni.proto.Bitcoin
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import kotlin.collections.first
import kotlin.collections.map
import kotlin.let

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
    StakeTcy,
    UnstakeTcy,
    StakeRuji,
    UnstakeRuji,
    WithdrawRujiRewards,
    MintYTCY,
    MintYRUNE,
    RedeemYRUNE,
    RedeemYTCY,
    SecuredAsset,
    WithdrawSecuredAsset
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
    val balance: UiText = UiText.Empty,
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

    val rewardsAmount: String? = null,

    val isAutoCompoundTcyStake: Boolean = false,
    val isAutoCompoundTcyUnStake: Boolean = false,

    val availableSecuredAssets:  List<TokenWithdrawSecureAsset> =  emptyList(),
    val selectedSecuredAsset: TokenWithdrawSecureAsset =  availableSecuredAssets.firstOrNull() ?: TokenWithdrawSecureAsset.EMPTY,
)

@HiltViewModel
internal class DepositFormViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val sendNavigator: Navigator<SendDst>,
    private val requestQrScan: RequestQrScanUseCase,
    private val mapTokenValueToStringWithUnit: TokenValueToStringWithUnitMapper,
    private val gasFeeRepository: GasFeeRepository,
    private val accountsRepository: AccountsRepository,
    private val isAssetCharsValid: DepositMemoAssetsValidatorUseCase,
    private val requestResultRepository: RequestResultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val transactionRepository: DepositTransactionRepository,
    private val blockChainSpecificRepository: BlockChainSpecificRepository,
    private val thorChainApi: ThorChainApi,
    private val mayaChainApi: MayaChainApi,
    private val balanceRepository: BalanceRepository,
    private val gasFeeToEstimatedFee: GasFeeToEstimatedFeeUseCase,
    private val enableCoin: EnableTokenUseCase,
    private val validateMayaTransactionHeight: ValidateMayaTransactionHeightUseCase,
    private val feeServiceComposite: FeeServiceComposite,
    private val vaultRepository: VaultRepository,
    private val tokenRepository: TokenRepository,
    private val gasFeeToEstimate: GasFeeToEstimatedFeeUseCaseImpl,
) : ViewModel() {

    private lateinit var vaultId: String
    private var chain: Chain? = null

    private var tcyAutoCompoundAmount: String? = null

    private var unstakableAmountCache: String? = null

    var isAutoCompoundTcyStake: Boolean
        get() = state.value.isAutoCompoundTcyStake
        set(value) {
            state.value = state.value.copy(isAutoCompoundTcyStake = value)
        }

    var isAutoCompoundTcyUnStake: Boolean
        get() = state.value.isAutoCompoundTcyUnStake
        set(value) {
            state.value = state.value.copy(isAutoCompoundTcyUnStake = value)
        }

    private var rujiMergeBalances = MutableStateFlow<List<MergeAccount>?>(null)
    private var rujiStakeBalances = MutableStateFlow<RujiStakeBalances?>(null)
    private val planBtc = MutableStateFlow<Bitcoin.TransactionPlan?>(null)

    val tokenAmountFieldState = TextFieldState()
    val nodeAddressFieldState = TextFieldState()
    val providerFieldState = TextFieldState()
    val operatorFeeFieldState = TextFieldState()
    val customMemoFieldState = TextFieldState()
    val basisPointsFieldState = TextFieldState()
    val lpUnitsFieldState = TextFieldState()
    val assetsFieldState = TextFieldState()
    val thorAddressFieldState = TextFieldState()
    val rewardsAmountFieldState = TextFieldState()
    val slippageFieldState = TextFieldState()

    val state = MutableStateFlow(DepositFormUiModel())
    var isLoading: Boolean
        get() = state.value.isLoading
        set(value) {
            state.update {
                it.copy(isLoading = value)
            }
        }

    private val address = MutableStateFlow<Address?>(null)
    private val secureAssetNodeValue = MutableStateFlow<String?>(null)
    private var addressJob: Job? = null
    private var depositTypeAction: String? = null
    private var bondAddress: String? = null

    fun loadData(
        vaultId: String,
        chainId: String,
        depositType: String?,
        bondAddress: String?,
    ) {
        this.vaultId = vaultId
        val chain = chainId.let(Chain::fromRaw)
        this.chain = chain
        this.depositTypeAction = depositType
        this.bondAddress = bondAddress

        val depositOptions = when (chain) {
            Chain.ThorChain -> listOf(
                DepositOption.Bond,
                DepositOption.Unbond,
                DepositOption.Leave,
                DepositOption.Custom,
                DepositOption.Merge,
                DepositOption.UnMerge,
                DepositOption.StakeTcy,
                DepositOption.UnstakeTcy,
                DepositOption.StakeRuji,
                DepositOption.UnstakeRuji,
                DepositOption.WithdrawRujiRewards,
                DepositOption.MintYTCY,
                DepositOption.MintYRUNE,
                DepositOption.RedeemYTCY,
                DepositOption.RedeemYRUNE,
                DepositOption.SecuredAsset,
                DepositOption.WithdrawSecuredAsset,
            )

            Chain.MayaChain -> listOf(
                DepositOption.Bond,
                DepositOption.Unbond,
                DepositOption.Leave,
                DepositOption.Custom,
                DepositOption.AddCacaoPool,
                DepositOption.RemoveCacaoPool,
            )

            Chain.Kujira, Chain.Osmosis -> listOf(
                DepositOption.TransferIbc,
            )

            Chain.GaiaChain -> listOf(
                DepositOption.TransferIbc,
                DepositOption.Switch,
            )
            else ->
                buildList {
//                    add(DepositOption.Stake)
//                    add(DepositOption.Unstake)
                    if (chain.ticker() in SECURE_ASSETS_TICKERS)
                        add(DepositOption.SecuredAsset)
                }
        }
        val depositOption = depositOptions.first()
        state.update {
            it.copy(
                depositMessage = R.string.deposit_message_deposit_title.asUiText(chain.raw),
                depositOptions = depositOptions,
                depositOption = depositOption,
                depositChain = chain
            )
        }

        val coinList = tokensToMerge
            .let {
                if (chain == Chain.Osmosis)
                    it.filter { it.ticker.equals("LVN", ignoreCase = true) }
                else
                    it
            }
        state.update {
            it.copy(
                selectedCoin = coinList.first(),
                coinList = coinList,
                selectedUnMergeCoin = coinList.first(),
            )
        }

        loadAddress(vaultId, chain)


        address.filterNotNull()
            .onEach { address ->
                val selectedToken = address.accounts.find { it.token.isNativeToken }?.token
                selectedToken?.let {
                    state.update {
                        it.copy(selectedToken = selectedToken)
                    }
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

                val account = when (depositOption) {
                    DepositOption.Switch, DepositOption.TransferIbc, DepositOption.Merge -> {
                        targetTicker = selectedMergeToken.ticker
                        address.accounts.find {
                            it.token.ticker.equals(
                                selectedMergeToken.ticker, ignoreCase = true
                            )
                        }
                    }

                    DepositOption.StakeTcy, DepositOption.UnstakeTcy, DepositOption.StakeRuji,
                    DepositOption.UnstakeRuji, DepositOption.WithdrawRujiRewards,
                    DepositOption.MintYTCY, DepositOption.MintYRUNE,
                    DepositOption.RedeemYTCY, DepositOption.RedeemYRUNE,
                    DepositOption.Custom,
                        -> {
                        targetTicker = selectedToken.ticker
                        address.accounts.find { it.token.id == selectedToken.id }
                    }

                    else -> {
                        val account = address.accounts.find { it.token.isNativeToken }
                        targetTicker = account?.token?.ticker
                        account
                    }
                }

                updateTokenAmount(account, chain, targetTicker, vaultId)

                if (depositOption == DepositOption.SecuredAsset) {
                    collectSecuredAssetAddresses()
                }

            }.collect {
                setMetadataInfo()
            }
        }

        viewModelScope.launch {
            combine(
                state.map { it.selectedCoin }
                    .distinctUntilChanged(),
                state.map { it.depositOption }
                    .distinctUntilChanged()
            ) { selectedMergeToken, depositOption ->
                when (depositOption) {
                    DepositOption.TransferIbc, DepositOption.Switch -> {
                        // special case, because of all supported merge tokens only lvn is osmosis native
                        val dstChainList = if (selectedMergeToken.ticker == "LVN") {
                            when (chain) {
                                Chain.Osmosis -> listOf(Chain.GaiaChain)
                                else -> listOf(Chain.Osmosis)
                            }
                        } else {
                            listOf(
                                Chain.GaiaChain, Chain.Kujira, Chain.Osmosis,
                                Chain.Noble, Chain.Akash,
                            ).filter { it != chain }
                        }

                        state.update {
                            it.copy(
                                dstChainList = dstChainList,
                            )
                        }

                        selectDstChain(dstChainList.first())
                    }

                    else -> Unit
                }
            }.collect {}
        }

        collectTcyStakeAutoCompound()
    }

    private fun setMetadataInfo() {
        if (!depositTypeAction.isNullOrEmpty()) {
            val action = parseDepositType(depositTypeAction)

            if (action != null) {
                val depositOption = when (action) {
                    DeFiNavActions.UNBOND -> DepositOption.Unbond
                    DeFiNavActions.WITHDRAW_RUJI -> DepositOption.WithdrawRujiRewards
                    DeFiNavActions.STAKE_RUJI -> DepositOption.StakeRuji
                    DeFiNavActions.UNSTAKE_RUJI -> DepositOption.UnstakeRuji
                    DeFiNavActions.STAKE_TCY -> DepositOption.StakeTcy
                    DeFiNavActions.UNSTAKE_TCY -> DepositOption.UnstakeTcy
                    DeFiNavActions.MINT_YRUNE -> DepositOption.MintYRUNE
                    DeFiNavActions.REDEEM_YRUNE -> DepositOption.RedeemYRUNE
                    DeFiNavActions.MINT_YTCY -> DepositOption.MintYTCY
                    DeFiNavActions.REDEEM_YTCY -> DepositOption.RedeemYTCY
                    else -> DepositOption.Bond
                }
                selectDepositOption(depositOption)

            } else {
                Timber.w("Unknown deposit type action: $depositTypeAction, using default flow")
            }
        }
    }

    private suspend fun updateTokenAmount(
        account: Account?,
        chain: Chain,
        targetTicker: String?,
        vaultId: String,
    ) {
        if (account != null) {
            account.tokenValue?.let { tokenValue ->
                val value = mapTokenValueToStringWithUnit(tokenValue)
                state.update { state ->
                    state.copy(
                        amountError = null,
                        balance = value.asUiText()
                    )
                }
            }
        } else {
            val token = findCoin(chain, targetTicker)
            token?.let {
                enableCoin(vaultId, token)
                loadAddress(vaultId, chain)
            } ?: run {
                state.update {
                    it.copy(
                        balance = UiText.Empty,
                        amountError = UiText.FormattedText(
                            R.string.must_be_enabled_before_proceeding,
                            listOf(targetTicker.orEmpty())
                        )
                    )
                }
            }
        }
    }

    private fun loadAddress(vaultId: String, chain: Chain) {
        addressJob?.cancel()
        addressJob = viewModelScope.launch {
            try {
                accountsRepository.loadAddress(vaultId, chain)
                    .collect { address ->
                        this@DepositFormViewModel.address.value = address
                    }
            } catch (e: Exception) {
                Timber.e(e)
            }
        }
    }

    fun selectToken() {
        val chain = chain ?: return

        viewModelScope.launch {
            val requestId = UUID.randomUUID().toString()

            navigator.route(
                Route.SelectAsset(
                    requestId = requestId,
                    vaultId = vaultId,
                    preselectedNetworkId = chain.id,
                    networkFilters = Route.SelectNetwork.Filters.DisableNetworkSelection
                )
            )
            val selectedAsset = requestResultRepository.request<AssetSelected?>(requestId)
            val selectedToken = selectedAsset?.token

            if (selectedToken != null) {
                state.update {
                    it.copy(
                        selectedToken = selectedToken,
                    )
                }
            }
        }
    }

    fun selectDepositOption(option: DepositOption) {
        viewModelScope.launch {
            resetTextFields()
            state.update {
                it.copy(depositOption = option)
            }

            when (option) {
                DepositOption.Switch -> {
                    viewModelScope.launch {
                        try {
                            val inboundAddresses = thorChainApi.getTHORChainInboundAddresses()
                            val inboundAddress = inboundAddresses
                                .firstOrNull { it.chain.equals("GAIA", ignoreCase = true) }
                            if (inboundAddress != null && inboundAddress.halted.not() &&
                                inboundAddress.chainLPActionsPaused.not() && inboundAddress.globalTradingPaused.not()
                            ) {
                                val gaiaAddress = inboundAddress.address
                                nodeAddressFieldState.setTextAndPlaceCursorAtEnd(gaiaAddress)
                            }
                            accountsRepository.loadAddress(vaultId, Chain.ThorChain)
                                .collect { addresses ->
                                    thorAddressFieldState.setTextAndPlaceCursorAtEnd(addresses.address)
                                }
                        } catch (e: Exception) {
                            Timber.e(e)
                        }
                    }

                }

                DepositOption.Bond, DepositOption.Unbond, DepositOption.Leave,
                DepositOption.MintYRUNE,
                    ->
                    state.update {
                        it.copy(selectedToken = Coins.ThorChain.RUNE, unstakableAmount = null)
                    }

                DepositOption.MintYTCY -> {
                    state.update {
                        it.copy(selectedToken = Coins.ThorChain.TCY)
                    }
                }

                DepositOption.RedeemYTCY -> {
                    val yTCY = Coins.getCoinBy(Chain.ThorChain, "yTCY") ?: return@launch
                    state.update {
                        it.copy(selectedToken = yTCY)
                    }
                    setSlippage(DEFAULT_SLIPPAGE)
                }

                DepositOption.RedeemYRUNE -> {
                    val yRUNE = Coins.getCoinBy(Chain.ThorChain, "yRUNE") ?: return@launch
                    state.update {
                        it.copy(selectedToken = yRUNE)
                    }
                    setSlippage(DEFAULT_SLIPPAGE)
                }

                DepositOption.StakeTcy, DepositOption.UnstakeTcy -> {
                    state.update {
                        it.copy(selectedToken = Coins.ThorChain.TCY, unstakableAmount = null)
                    }
                }

                DepositOption.StakeRuji -> {
                    val rujiToken = Coins.getCoinBy(Chain.ThorChain, "RUJI") ?: return@launch
                    state.update {
                        it.copy(selectedToken = rujiToken)
                    }
                }

                DepositOption.UnstakeRuji -> {
                    handleRujiDepositOption(DepositOption.UnstakeRuji)
                }

                DepositOption.WithdrawRujiRewards -> {
                    handleRujiDepositOption(DepositOption.WithdrawRujiRewards)
                }

                DepositOption.RemoveCacaoPool -> {
                    handleRemoveCacaoOption()
                }

                DepositOption.WithdrawSecuredAsset ->{
                    viewModelScope.launch {
                        accountsRepository.loadAddress(vaultId, Chain.ThorChain)
                            .collect { addresses ->
                                thorAddressFieldState.setTextAndPlaceCursorAtEnd(addresses.address)
                            }

                        accountsRepository.loadAddress(
                            vaultId = vaultId,
                            chain = Chain.ThorChain,
                        ).catch {
                            Timber.e(it)
                        }.collect { address ->
                            val availableSecuredAssets = address.accounts.filter { account ->
                                account.token.isSecuredAsset()
                            }.map {
                                TokenWithdrawSecureAsset(
                                    ticker = it.token.ticker,
                                    contract = it.token.contractAddress,
                                    coin = it.token,
                                    tokenValue = it.tokenValue
                                )
                            }
                            if (availableSecuredAssets.isNotEmpty()) {
                                val selectedSecuredAsset = availableSecuredAssets.first()
                                val balance = selectedSecuredAsset.tokenValue?.let(
                                    mapTokenValueToStringWithUnit
                                )
                                state.update {
                                    it.copy(
                                        availableSecuredAssets = availableSecuredAssets,
                                        selectedSecuredAsset = selectedSecuredAsset,
                                        balance = balance?.asUiText() ?: UiText.Empty,
                                    )
                                }
                            }
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

    private fun collectSecuredAssetAddresses() {
        viewModelScope.launch {
            val (thorAddress) = chainAccountAddressRepository.getAddress(
                chain = Chain.ThorChain,
                vault = vaultRepository.get(vaultId)!!
            )

            thorAddressFieldState.setTextAndPlaceCursorAtEnd(thorAddress)

            val inboundAddresses = thorChainApi.getTHORChainInboundAddresses()
            val inboundAddress = inboundAddresses
                .firstOrNull {
                    it.chain.equals(
                        state.value.selectedToken.getChainName(),
                        ignoreCase = true
                    )
                }

            if (inboundAddress != null && inboundAddress.halted.not() &&
                inboundAddress.chainLPActionsPaused.not() && inboundAddress.globalTradingPaused.not()
            ) {
                val inboundChainAddress = inboundAddress.address
                secureAssetNodeValue.value = inboundChainAddress
            }
        }
    }

    private suspend fun handleRemoveCacaoOption() {
        val addressValue = address.value?.address ?: return
        try {
            val balance = mayaChainApi.getUnStakeCacaoBalance(addressValue)
            balance?.let {
                val balanceInt = it.toBigIntegerOrNull()
                if (balanceInt == null) {
                    Timber.e("Invalid balance format: $it")
                    state.update { state ->
                        state.copy(unstakableAmount = null)
                    }
                    return
                }
                val unstakableAmount = mapTokenValueToStringWithUnit(
                    TokenValue(
                        value = balanceInt,
                        token = Coins.MayaChain.CACAO
                    )
                )
                state.update { state ->
                    state.copy(
                        unstakableAmount = unstakableAmount
                    )
                }
            } ?: run {
                state.update { state ->
                    state.copy(unstakableAmount = null)
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch unstakable CACAO balance")
            state.update { state ->
                state.copy(
                    unstakableAmount = null,
                    errorText = UiText.StringResource(R.string.dialog_default_error_body)
                )
            }
        }
    }

    private fun collectTcyStakeAutoCompound() {
        combine(
            state.map { it.depositOption }.distinctUntilChanged(),
            state.map { it.isAutoCompoundTcyUnStake }.distinctUntilChanged(),
            address.filterNotNull().map { it.address }.distinctUntilChanged(),
        ) { option, isAuto, addr ->
            Triple(
                option,
                isAuto,
                addr
            )
        }
            .filter { (option, _, _) -> option == DepositOption.UnstakeTcy }
            .onEach { (_, isAuto, addressValue) ->
                try {
                    val raw = if (isAuto) {
                        tcyAutoCompoundAmount
                            ?: balanceRepository.getTcyAutoCompoundAmount(addressValue)
                                .also { tcyAutoCompoundAmount = it }
                    } else {
                        unstakableAmountCache
                            ?: balanceRepository.getUnstakableTcyAmount(addressValue)
                                .also { unstakableAmountCache = it }
                    }
                    val formattedAmount = formatUnstakableTcyAmount(raw)
                    state.update { it.copy(unstakableAmount = formattedAmount) }
                } catch (e: Exception) {
                    Timber.e(e)
                    state.update { it.copy(unstakableAmount = null) }
                }
            }
            .launchIn(viewModelScope)
    }

    fun selectDstChain(chain: Chain) {
        nodeAddressFieldState.clearText()

        state.update {
            it.copy(selectedDstChain = chain)
        }

        viewModelScope.launch {
            val address = accountsRepository.loadAddress(vaultId, chain)
                .firstOrNull()

            if (address != null) {
                nodeAddressFieldState.setTextAndPlaceCursorAtEnd(address.address)
            }
        }
    }

    fun selectMergeToken(mergeInfo: TokenMergeInfo) {
        state.update {
            it.copy(selectedCoin = mergeInfo)
        }
    }

    fun selectUnMergeToken(unmergeInfo: TokenMergeInfo) {
        state.update {
            it.copy(selectedUnMergeCoin = unmergeInfo)
        }
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
        state.update { it.copy(tokenAmountError = null) }
    }

    fun validateNodeAddress() {
        val errorText = validateDstAddress(nodeAddressFieldState.text.toString())
        state.update {
            it.copy(nodeAddressError = errorText)
        }
    }

    fun validateTokenAmount() {
        val errorText = validateTokenAmount(tokenAmountFieldState.text.toString())
        state.update { it.copy(tokenAmountError = errorText) }
    }

    fun validateProvider() {
        val errorText = validateDstAddress(providerFieldState.text.toString())
        state.update {
            it.copy(providerError = errorText)
        }
    }

    fun validateOperatorFee() {
        val text = operatorFeeFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = validateBasisPoints(text.toIntOrNull())
            state.update {
                it.copy(operatorFeeError = errorText)
            }
        }
    }

    fun validateCustomMemo() {
        val errorText = validateCustomMemo(customMemoFieldState.text.toString())
        state.update {
            it.copy(customMemoError = errorText)
        }
    }

    fun validateBasisPoints() {
        val text = basisPointsFieldState.text.toString()
        if (text.isNotEmpty()) {
            val errorText = validateBasisPoints(text.toIntOrNull())
            state.update {
                it.copy(basisPointsError = errorText)
            }
        }
    }

    fun validateSlippage() {
        val text = slippageFieldState.text.toString()
        val errorText = validateSlippage(text)
        state.update {
            it.copy(slippageError = errorText)
        }
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
    }

    private fun setSlippage(slippage: String) {
        slippageFieldState.setTextAndPlaceCursorAtEnd(slippage)
    }

    fun scan() {
        viewModelScope.launch {
            val qr = requestQrScan()
            if (!qr.isNullOrBlank()) {
                nodeAddressFieldState.setTextAndPlaceCursorAtEnd(qr)
            }
        }
    }

    fun dismissError() {
        state.update { it.copy(errorText = null) }
    }

    fun deposit() {
        viewModelScope.launch {
            try {
                isLoading = true
                val depositOption = state.value.depositOption

                // Validate percentage input for TCY unstaking
                if (depositOption == DepositOption.UnstakeTcy) {
                    val percentageText = tokenAmountFieldState.text.toString()
                    val percentage = percentageText.toFloatOrNull()
                        ?: throw InvalidTransactionDataException(UiText.StringResource(R.string.send_error_no_amount))

                    val error = when {
                        percentage <= 0 -> {
                            UiText.StringResource(R.string.send_error_no_amount)
                        }

                        percentage > 100 -> {
                            UiText.StringResource(R.string.deposit_error_max_amount)
                        }

                        else -> null
                    }

                    error?.let {
                        throw InvalidTransactionDataException(it)
                    }
                }

                val transaction = when (depositOption) {
                    DepositOption.AddCacaoPool -> createAddCacaoPoolTransaction()
                    DepositOption.Bond -> createBondTransaction()
                    DepositOption.Unbond -> createUnbondTransaction()
                    DepositOption.Leave -> createLeaveTransaction()
                    DepositOption.Custom -> createCustomTransaction()
                    DepositOption.Stake -> createStakeTransaction()
                    DepositOption.Unstake -> createUnstakeTransaction()
                    DepositOption.TransferIbc -> createTransferIbcTx()
                    DepositOption.Switch -> createSwitchTx()
                    DepositOption.Merge -> createMergeTx()
                    DepositOption.UnMerge -> createUnMergeTx()
                    DepositOption.StakeTcy -> createTcyStakeTx(
                        if (isAutoCompoundTcyStake) "" else "TCY+",
                        isUnStake = false,
                        percentage = null
                    )

                    DepositOption.UnstakeTcy -> {
                        // Get percentage from user input
                        val percentageText = tokenAmountFieldState.text.toString()
                        val percentage =
                            percentageText.toFloatOrNull()
                                ?: 100f // Default to 100% if invalid
                        val basisPoints = (percentage * 100).toInt()
                            .coerceIn(0, 10000) // Convert to basis points (0-10000)
                        createTcyStakeTx(
                            if (isAutoCompoundTcyUnStake) "" else "TCY-:$basisPoints",
                            isUnStake = true,
                            percentage = percentage
                        )
                    }

                    DepositOption.StakeRuji -> createStakeRuji()
                    DepositOption.UnstakeRuji -> createUnstakeRuji()
                    DepositOption.WithdrawRujiRewards -> createWithdrawRewardsRuji()
                    DepositOption.MintYTCY -> createReceiveYToken(DepositOption.MintYTCY)
                    DepositOption.MintYRUNE -> createReceiveYToken(DepositOption.MintYRUNE)
                    DepositOption.RedeemYRUNE -> createSellYToken(DepositOption.RedeemYRUNE)
                    DepositOption.RedeemYTCY -> createSellYToken(DepositOption.RedeemYTCY)
                    DepositOption.RemoveCacaoPool -> createRemoveCacaoPoolTransaction()
                    DepositOption.SecuredAsset -> createSecuredAssetTransaction()
                    DepositOption.WithdrawSecuredAsset -> createWithdrawSecuredAssetTransaction()
                }

                transactionRepository.addTransaction(transaction)

                sendNavigator.navigate(
                    SendDst.VerifyTransaction(
                        transactionId = transaction.id,
                        vaultId = vaultId,
                    )
                )
            } catch (e: InvalidTransactionDataException) {
                showError(e.text)
            } catch (e: Exception) {
                Timber.e(e)
                showError(UiText.StringResource(R.string.dialog_default_error_body))
                // Error occurred during deposit operation
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun createSellYToken(depositOption: DepositOption): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        val address = address.value ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedAccount = getSelectedAccount() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val slippage = slippageFieldState.text.toString()
        val slippageError = validateSlippage(slippage)
        if (slippageError != null) {
            throw InvalidTransactionDataException(slippageError)
        }

        val selectedToken = selectedAccount.token
        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)
        val tokenAmount =
            requireTokenAmount(selectedToken, selectedAccount, address, gasFee)

        val memo = "sell:${selectedToken.contractAddress}:$tokenAmount"

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)
        val contractAddress = when (depositOption) {
            DepositOption.RedeemYTCY -> {
                YTCY_CONTRACT
            }

            DepositOption.RedeemYRUNE -> {
                YRUNE_CONTRACT
            }

            else -> {
                throw RuntimeException("Invalid Deposit Parameter ")
            }
        }

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = contractAddress,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmount,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = ThorchainFunctions.redeemYToken(
                fromAddress = srcAddress,
                tokenContract = contractAddress,
                slippage = slippage.formatSlippage(),
                denom = selectedToken.contractAddress,
                amount = tokenAmount,
            )
        )
    }

    private fun String.formatSlippage(): String {
        val divider = "100".toBigDecimal()
        return try {
            this.toBigDecimal()
                .setScale(2, RoundingMode.DOWN)
                .divide(divider)
                .toPlainString()
        } catch (t: Throwable) {
            "0.01" // Default slippage for safety
        }
    }

    private suspend fun createReceiveYToken(depositOption: DepositOption): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        val address = address.value ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedAccount = getSelectedAccount() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedToken = selectedAccount.token
        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)
        val tokenAmount =
            requireTokenAmount(selectedToken, selectedAccount, address, gasFee)

        val memo = "receive:${selectedToken.ticker.lowercase()}:$tokenAmount"

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        val tokenContract = when (depositOption) {
            DepositOption.MintYRUNE -> {
                YRUNE_CONTRACT
            }

            DepositOption.MintYTCY -> {
                YTCY_CONTRACT
            }

            else -> {
                throw RuntimeException("Invalid Deposit Parameter ")
            }
        }

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = YRUNE_YTCY_AFFILIATE_CONTRACT,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmount,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = ThorchainFunctions.mintYToken(
                fromAddress = srcAddress,
                stakingContract = YRUNE_YTCY_AFFILIATE_CONTRACT,
                tokenContract = tokenContract,
                denom = selectedToken.ticker.lowercase(),
                amount = tokenAmount,
            )
        )
    }

    private suspend fun createWithdrawRewardsRuji(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val selectedAccount = getSelectedAccount() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedToken = selectedAccount.token
        val srcAddress = selectedToken.address
        val tokenAmount = fetchRujiStakeBalances(srcAddress).rewardsAmount

        if (tokenAmount <= BigInteger.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)
        val memo = "claim:${selectedToken.contractAddress}:$tokenAmount"

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = STAKING_RUJI_CONTRACT,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmount,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = ThorchainFunctions.claimRujiRewards(
                fromAddress = srcAddress,
                stakingContract = STAKING_RUJI_CONTRACT,
            )
        )
    }

    private suspend fun createUnstakeRuji(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val selectedAccount = getSelectedAccount() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedToken = selectedAccount.token
        val srcAddress = selectedToken.address

        val stakeBalance = fetchRujiStakeBalances(srcAddress).stakeAmount

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()?.let { CoinType.THORCHAIN.toUnit(it) }

        if (tokenAmount == null || tokenAmount <= BigInteger.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        if (tokenAmount > stakeBalance) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_max_shares)
            )
        }

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)
        val memo = "withdraw:${selectedToken.contractAddress}:$tokenAmount"

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = STAKING_RUJI_CONTRACT,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmount,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = ThorchainFunctions.unstakeRUJI(
                fromAddress = srcAddress,
                stakingContract = STAKING_RUJI_CONTRACT,
                amount = tokenAmount.toString(),
            )
        )
    }

    private suspend fun createStakeRuji(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        val address = address.value ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )


        val selectedAccount = getSelectedAccount() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedToken = selectedAccount.token
        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)
        val tokenAmount =
            requireTokenAmount(selectedToken, selectedAccount, address, gasFee)

        val memo = "bond:${selectedToken.contractAddress}:$tokenAmount"

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = STAKING_RUJI_CONTRACT,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmount,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = ThorchainFunctions.stakeRUJI(
                srcAddress,
                STAKING_RUJI_CONTRACT,
                selectedToken.contractAddress,
                tokenAmount
            )
        )
    }

    private suspend fun createUnMergeTx(): DepositTransaction {
        val unmergeToken = state.value.selectedUnMergeCoin
        val unMergeAccountBalance = rujiMergeBalances.value
            ?.firstOrNull {
                it.pool?.mergeAsset?.metadata?.symbol.equals(
                    unmergeToken.ticker,
                    true
                )
            }
        val maxShares = unMergeAccountBalance?.shares?.toBigInteger() ?: BigInteger.ZERO

        // transform amount back to share units
        val tokenShares = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()?.let { CoinType.THORCHAIN.toUnit(it) }

        if (tokenShares == null || tokenShares <= BigInteger.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        if (tokenShares > maxShares) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_max_shares)
            )
        }

        val chain = chain ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )
        val address = address.value ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val account = address.accounts
            .find { it.token.ticker.equals(unmergeToken.ticker, ignoreCase = true) }
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.merge_account_doesnt_exist)
            )

        val srcAddress = account.token.address
        val dstAddr = unmergeToken.contract
        val memo = "unmerge:${unmergeToken.denom}:${tokenShares}"
        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                account.token,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_THOR_UNMERGE,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, account.token)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = account.token,
            srcAddress = srcAddress,
            dstAddress = dstAddr,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenShares,
                token = account.token,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createAddCacaoPoolTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = accountsRepository.loadAddress(
            vaultId,
            chain
        )
            .first()
        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        if (selectedToken.ticker != "CACAO") {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.cacaopool_only_cacao_supported)
            )
        }

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }
        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(
            chain,
            srcAddress
        )
        val memo = DepositMemo.DepositPool

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        val gasFeeFiat = getFeesFiatValue(
            specific,
            gasFee,
            selectedToken
        )


        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = "",

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        )
    }


    @OptIn(ExperimentalStdlibApi::class)
    private suspend fun createSecuredAssetTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val thorAddress = thorAddressFieldState.text.toString()

        val selectedAccount = getSelectedAccount() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedToken = selectedAccount.token

        if (!selectedAccount.token.isSecuredAsset()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_not_secured_asset)
            )
        }

        val srcAddress = selectedToken.address

        val dstAddr = secureAssetNodeValue.value
        if (dstAddr.isNullOrBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }
        val memo = "SECURE+:$thorAddress"

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()


        val vault = withContext(Dispatchers.IO) {
            vaultRepository.get(vaultId)
        } ?: error("Vault not found")

        val blockchainTransaction = Transfer(
            coin = selectedToken,
            vault = VaultData(
                vaultHexChainCode = vault.hexChainCode,
                vaultHexPublicKey = vault.getPubKeyByChain(chain),
            ),
            amount = tokenAmountInt,
            to = dstAddr,
            memo = memo,
            isMax = false,
        )

        val fees = withContext(Dispatchers.IO) {
            feeServiceComposite.calculateFees(blockchainTransaction)
        }
        val nativeCoin = withContext(Dispatchers.IO) {
            tokenRepository.getNativeToken(chain.id)
        }
        val fromGas = GasFeeParams(
            gasLimit = BigInteger.ONE,
            gasFee = TokenValue(
                value = fees.amount,
                token = nativeCoin,
            ),
            selectedToken = selectedToken,
        )
        val gasFee = TokenValue(
            value = fees.amount,
            token = nativeCoin,
        )

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                memo = memo,
                isSwap = false,
                dstAddress = dstAddr,
                isMaxAmountEnabled = false,
                isDeposit = true,
                tokenAmountValue = tokenAmountInt
            ).let { specific ->
                if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
                    planBtc.value ?: getBitcoinTransactionPlan(
                        vaultId = vaultId,
                        selectedToken = selectedToken,
                        dstAddress = dstAddr,
                        tokenAmountInt = tokenAmountInt,
                        specific = specific,
                        memo = memo
                    ).also { plan ->
                        planBtc.value = plan
                    }

                    selectUtxosIfNeeded(
                        chain,
                        specific
                    )
                } else {
                    specific
                }
            }
        if (chain.standard == TokenStandard.UTXO && chain != Chain.Cardano) {
            validateBtcLikeAmount(tokenAmountInt, chain)
        }
        val estimatedGasFee = gasFeeToEstimate.invoke(fromGas)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = estimatedGasFee.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            utxos = specific.utxos,
            thorAddress = thorAddress,
            operation = OPERATION_MINT,
            )
    }

    private suspend fun createWithdrawSecuredAssetTransaction(): DepositTransaction {
        val chain = chain ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val thorAddress = thorAddressFieldState.text.toString()
        if (thorAddress.isBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.thorchain_address_not_found_in_vault)
            )
        }

        val selectedSecureAsset = state.value.selectedSecuredAsset

        val dstAddr = accountsRepository.loadAddress(vaultId,selectedSecureAsset.ticker.getChain() )
            .firstOrNull() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        if (dstAddr.address.isBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val selectedToken = selectedSecureAsset.coin

        val memo = "SECURE-:${dstAddr.address}"

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()


        if ((selectedSecureAsset.tokenValue?.value ?: BigInteger.ZERO) < tokenAmountInt) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_insufficient_balance)
            )
        }


        val vault = withContext(Dispatchers.IO) {
            vaultRepository.get(vaultId)
        } ?: error("Vault not found")

        val blockchainTransaction = Transfer(
            coin = selectedToken,
            vault = VaultData(
                vaultHexChainCode = vault.hexChainCode,
                vaultHexPublicKey = vault.getPubKeyByChain(chain),
            ),
            amount = tokenAmountInt,
            to = dstAddr.address,
            memo = memo,
            isMax = false,
        )

        val fees = withContext(Dispatchers.IO) {
            feeServiceComposite.calculateFees(blockchainTransaction)
        }
        val nativeCoin = withContext(Dispatchers.IO) {
            tokenRepository.getNativeToken(chain.id)
        }
        val fromGas = GasFeeParams(
            gasLimit = BigInteger.ONE,
            gasFee = TokenValue(
                value = fees.amount,
                token = nativeCoin,
            ),
            selectedToken = selectedToken,
        )
        val gasFee = TokenValue(
            value = fees.amount,
            token = nativeCoin,
        )

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                thorAddress,
                selectedToken,
                gasFee,
                memo = memo,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                tokenAmountValue = tokenAmountInt,
                )

        val estimatedGasFee = gasFeeToEstimate.invoke(fromGas)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = thorAddress,
            dstAddress = "",
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = estimatedGasFee.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            operation = OPERATION_WITHDRAW,
        )
    }

    private suspend fun createRemoveCacaoPoolTransaction(): DepositTransaction {

        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = accountsRepository.loadAddress(
            vaultId,
            chain
        )
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val srcAddress = selectedToken.address

        validateMayaTransactionHeight(srcAddress) || throw InvalidTransactionDataException(
            UiText.StringResource(R.string.deposit_error_has_not_reached_maturity)
        )

        val gasFee = gasFeeRepository.getGasFee(
            chain,
            srcAddress
        )

        val basisPoints = tokenAmountFieldState.text.toString()
            .toIntOrNull()

        validateBasisPoints(basisPoints)?.let {
            throw InvalidTransactionDataException(it)
        }

        val memo = DepositMemo.WithdrawPool(
            basisPoints = basisPoints!! * 100, // 10000 BP = 100%; basisPoints in 0..100
        )

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )
        val gasFeeFiat = getFeesFiatValue(
            specific,
            gasFee,
            selectedToken
        )

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,

            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = "",

            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = BigInteger.ZERO,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        )

    }

    private suspend fun createBondTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val depositChain = state.value.depositChain

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() ||
            !chainAccountAddressRepository.isValid(chain, nodeAddress)
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (depositChain == Chain.ThorChain && (tokenAmount == null || tokenAmount <= BigDecimal.ZERO)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val assets = assetsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isAssetCharsValid(assets)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_assets)
            )
        }

        val lpUnits = lpUnitsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isLpUnitCharsValid(lpUnits)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_lpunits)
            )
        }

        val operatorFeeAmount = operatorFeeFieldState.text
            .toString()
            .toBigDecimalOrNull()

        val selectedToken = getSelectedToken() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val tokenAmountInt =
            tokenAmount
                ?.movePointRight(selectedToken.decimal)
                ?.toBigInteger() ?: BigInteger.ONE

        val operatorFeeValue = operatorFeeAmount
            ?.movePointRight(if (depositChain == Chain.ThorChain) 2 else 0)
            ?.toInt()

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val providerText = providerFieldState.text.toString()
        val provider = providerText.ifBlank { null }

        val memo = when (depositChain) {
            Chain.MayaChain -> Bond.Maya(
                nodeAddress = nodeAddress,
                providerAddress = provider,
                lpUnits = lpUnits.toIntOrNull(),
                assets = assets
            )

            Chain.ThorChain -> Bond.Thor(
                nodeAddress = nodeAddress,
                providerAddress = provider,
                operatorFee = operatorFeeValue,
            )

            else -> error("chain is invalid")
        }

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = nodeAddress,
            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private fun getSelectedToken(): Coin? {
        return getSelectedAccount()?.token
    }

    private fun getSelectedAccount(): Account? {
        val address = address.value ?: return null
        val userSelectedToken = state.value.selectedToken
        return address.accounts
            .first { it.token.id == userSelectedToken.id }
    }

    private suspend fun createUnbondTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val depositChain = state.value.depositChain

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() ||
            !chainAccountAddressRepository.isValid(chain, nodeAddress)
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val assets = assetsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isAssetCharsValid(assets)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_assets)
            )
        }

        val lpUnits = lpUnitsFieldState.text.toString()

        if (depositChain == Chain.MayaChain && !isLpUnitCharsValid(lpUnits)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_lpunits)
            )
        }

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (depositChain == Chain.ThorChain && (tokenAmount == null || tokenAmount <= BigDecimal.ZERO)) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val selectedToken = getSelectedToken() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val tokenAmountInt =
            tokenAmount
                ?.movePointRight(selectedToken.decimal)
                ?.toBigInteger() ?: BigInteger.ONE

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val providerText = providerFieldState.text.toString()
        val provider = providerText.ifBlank { null }

        val memo = when (state.value.depositChain) {
            Chain.MayaChain -> Unbond.Maya(
                nodeAddress = nodeAddress,
                providerAddress = provider,
                assets = assets,
                lpUnits = lpUnits.toIntOrNull(),
            )

            Chain.ThorChain -> Unbond.Thor(
                nodeAddress = nodeAddress,
                srcTokenValue = TokenValue(
                    value = tokenAmountInt,
                    token = selectedToken,
                ),
                providerAddress = provider,
            )

            else -> error("chain is invalid")
        }

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = nodeAddress,
            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = (chain == Chain.MayaChain)
                    .let { if (it) 1.toBigInteger() else BigInteger.ZERO },
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createLeaveTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() ||
            !chainAccountAddressRepository.isValid(chain, nodeAddress)
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val selectedToken = getSelectedToken() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val memo = DepositMemo.Leave(
            nodeAddress = nodeAddress,
        )

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = nodeAddress,
            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = (chain == Chain.MayaChain)
                    .let { if (it) 1.toBigInteger() else BigInteger.ZERO },
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            blockChainSpecific = specific.blockChainSpecific,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
        )
    }

    private suspend fun createCustomTransaction(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val selectedToken = getSelectedToken() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val memo = DepositMemo.Custom(
            memo = customMemoFieldState.text.toString(),
        )

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount < BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = "",
            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createTcyStakeTx(
        stakeMemo: String,
        isUnStake: Boolean,
        percentage: Float?,
    ): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        val address = address.value ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedAccount = getSelectedAccount() ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedToken = selectedAccount.token
        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        // For unstaking (TCY-:XXXX), we send zero amount - gas is covered by RUNE
        // For staking (TCY+), we send the full amount entered by user
        val tokenAmountInt = if (isUnStake) {
            // Get the appropriate unstakable amount based on compound type
            val totalUnits = if (isAutoCompoundTcyUnStake) {
                tcyAutoCompoundAmount?.toBigIntegerOrNull()
            } else {
                unstakableAmountCache?.toBigIntegerOrNull()
            }
                ?: throw InvalidTransactionDataException(UiText.StringResource(R.string.unstake_tcy_zero_error))

            if (totalUnits < BigInteger.ONE) {
                throw InvalidTransactionDataException(UiText.StringResource(R.string.unstake_tcy_zero_error))
            }
            // For unstaking, send zero TCY as gas is covered by RUNE
            BigInteger.ZERO
        } else {
            // For staking or other operations, validate and send the full amount
            requireTokenAmount(selectedToken, selectedAccount, address, gasFee)
        }

        val memo = stakeMemo

        val isAutoCompound = if (isUnStake) {
            isAutoCompoundTcyUnStake
        } else {
            isAutoCompoundTcyStake
        }

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = if (isAutoCompound)
                    TransactionType.TRANSACTION_TYPE_GENERIC_CONTRACT
                else TransactionType.TRANSACTION_TYPE_UNSPECIFIED
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)


        val wasmExecuteContractPayload = if (isAutoCompound)
            getWasmExecuteContractPayload(
                isUnStake,
                percentage,
                srcAddress,
                selectedToken,
                tokenAmountInt
            )
        else null

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = STAKING_TCY_COMPOUND_CONTRACT,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
            wasmExecuteContractPayload = wasmExecuteContractPayload
        )
    }

    private fun getWasmExecuteContractPayload(
        isUnStake: Boolean,
        percentage: Float?,
        srcAddress: String,
        selectedToken: Coin,
        tokenAmountInt: BigInteger,
    ): WasmExecuteContractPayload? {
        return if (isUnStake) {
            val units = percentage?.toBigDecimal()
                ?.times(tcyAutoCompoundAmount?.toBigDecimal() ?: BigDecimal.ZERO)
                ?.div(BigDecimal.valueOf(100))?.toBigInteger() ?: return null

            ThorchainFunctions.unStakeTcyCompound(
                units = units,
                stakingContract = STAKING_TCY_COMPOUND_CONTRACT,
                fromAddress = srcAddress
            )
        } else ThorchainFunctions.stakeTcyCompound(
            fromAddress = srcAddress,
            stakingContract = STAKING_TCY_COMPOUND_CONTRACT,
            denom = selectedToken.contractAddress,
            amount = tokenAmountInt
        )
    }


    private suspend fun createTonDepositTransaction(memo: DepositMemo): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val depositChain = state.value.depositChain

        if (depositChain != Chain.Ton) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.error_invalid_chain)
            )
        }

        val nodeAddress = nodeAddressFieldState.text.toString()

        if (nodeAddress.isBlank() ||
            !chainAccountAddressRepository.isValid(chain, nodeAddress)
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )
        }

        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }
        val address = accountsRepository.loadAddress(vaultId, chain)
            .first()

        val selectedToken = address.accounts.first { it.token.isNativeToken }.token

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                ?.toBigInteger() ?: BigInteger.ONE

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = nodeAddress,
            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmountInt,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createStakeTransaction(): DepositTransaction =
        createTonDepositTransaction(DepositMemo.Stake)

    private suspend fun createUnstakeTransaction(): DepositTransaction =
        createTonDepositTransaction(DepositMemo.Unstake)

    private suspend fun createMergeTx(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = address.value ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val mergeToken = state.value.selectedCoin

        val selectedAccount = address.accounts
            .find { it.token.ticker.equals(mergeToken.ticker, ignoreCase = true) }
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.merge_account_doesnt_exist)
            )

        val selectedToken = selectedAccount.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val dstAddr = mergeToken.contract

        val memo = "merge:${mergeToken.denom}"

        val tokenAmount =
            requireTokenAmount(selectedToken, selectedAccount, address, gasFee)

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_THOR_MERGE,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmount,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createSwitchTx(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = address.value ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )

        val selectedMergeToken = state.value.selectedCoin
        val selectedAccount = address.accounts
            .first { it.token.ticker.equals(selectedMergeToken.ticker, ignoreCase = true) }
        val selectedToken = selectedAccount.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)

        val dstAddr = nodeAddressFieldState.text.toString()

        val memo = "SWITCH:${thorAddressFieldState.text}"

        val tokenAmount =
            requireTokenAmount(selectedToken, selectedAccount, address, gasFee)

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain,
                srcAddress,
                selectedToken,
                gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_UNSPECIFIED,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,
            memo = memo,
            srcTokenValue = TokenValue(
                value = tokenAmount,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    private suspend fun createTransferIbcTx(): DepositTransaction {
        val chain = chain
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_address)
            )

        val address = address.value ?: throw InvalidTransactionDataException(
            UiText.StringResource(R.string.send_error_no_address)
        )
        val dstAddr = nodeAddressFieldState.text.toString()
        if (dstAddr.isBlank()) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_destination_address)
            )
        }

        if (!chainAccountAddressRepository.isValid(
                state.value.selectedDstChain,
                dstAddr
            )
        ) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.deposit_error_invalid_destination_address)
            )
        }

        val selectedMergeToken = state.value.selectedCoin
        val selectedAccount = address.accounts
            .first { it.token.ticker.equals(selectedMergeToken.ticker, ignoreCase = true) }
        val selectedToken = selectedAccount.token

        val srcAddress = selectedToken.address

        val gasFee = gasFeeRepository.getGasFee(chain, srcAddress)


        val memo = DepositMemo.TransferIbc(
            srcChain = chain,
            dstChain = state.value.selectedDstChain,
            dstAddress = dstAddr,

            memo = customMemoFieldState.text.toString()
                .takeIf { it.isNotBlank() },
        )

        val tokenAmount =
            requireTokenAmount(selectedToken, selectedAccount, address, gasFee)

        val specific = blockChainSpecificRepository
            .getSpecific(
                chain = chain,
                address = srcAddress,
                token = selectedToken,
                gasFee = gasFee,
                isSwap = false,
                isMaxAmountEnabled = false,
                isDeposit = true,
                transactionType = TransactionType.TRANSACTION_TYPE_IBC_TRANSFER,
            )

        val gasFeeFiat = getFeesFiatValue(specific, gasFee, selectedToken)

        return DepositTransaction(
            id = UUID.randomUUID().toString(),
            vaultId = vaultId,
            srcToken = selectedToken,
            srcAddress = srcAddress,
            dstAddress = dstAddr,
            memo = memo.toString(),
            srcTokenValue = TokenValue(
                value = tokenAmount,
                token = selectedToken,
            ),
            estimatedFees = gasFee,
            estimateFeesFiat = gasFeeFiat.formattedFiatValue,
            blockChainSpecific = specific.blockChainSpecific,
        )
    }

    fun onLoadRujiMergeBalances() {
        viewModelScope.launch {
            try {
                val selectedToken = state.value.selectedUnMergeCoin
                val addressString = address.value?.address
                    ?: throw RuntimeException("Invalid address: cannot fetch balance")

                withContext(Dispatchers.IO) {
                    val newBalances = thorChainApi.getRujiMergeBalances(addressString)
                    rujiMergeBalances.update { newBalances }
                }

                setUnMergeTokenSharesField(selectedToken)
            } catch (t: Throwable) {
                state.update {
                    it.copy(sharesBalance = UiText.Empty)
                }
                Timber.e("Can't load Ruji Balances ${t.message}")
            } finally {
                isLoading = false
            }
        }
    }

    private fun setUnMergeTokenSharesField(selectedToken: TokenMergeInfo) {
        val selectedSymbol = selectedToken.ticker
        val selectedMergeAccount = rujiMergeBalances.value
            ?.firstOrNull {
                it.pool?.mergeAsset?.metadata?.symbol.equals(selectedSymbol, true)
            } ?: return

        val amountText = selectedMergeAccount.shares
            ?.toBigInteger()
            ?.let { CoinType.THORCHAIN.toValue(it).toString() }
            ?: "0"

        state.update {
            it.copy(sharesBalance = amountText.asUiText())
        }

        tokenAmountFieldState.setTextAndPlaceCursorAtEnd(amountText)
    }

    private fun requireTokenAmount(
        selectedToken: Coin,
        selectedAccount: Account,
        address: Address,
        gas: TokenValue,
    ): BigInteger {
        val tokenAmount = tokenAmountFieldState.text
            .toString()
            .toBigDecimalOrNull()

        if (tokenAmount == null || tokenAmount <= BigDecimal.ZERO) {
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_amount)
            )
        }

        val tokenAmountInt =
            tokenAmount
                .movePointRight(selectedToken.decimal)
                .toBigInteger()

        val nativeTokenAccount = address.accounts
            .find { it.token.isNativeToken && it.token.chain == chain }
        val nativeTokenValue = nativeTokenAccount?.tokenValue?.value
            ?: throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_no_token)
            )

        if ((selectedAccount.tokenValue?.value ?: BigInteger.ZERO) < tokenAmountInt) {
            // For UnstakeTCY operations, check against the unstakable amount instead of the wallet balance
            if (state.value.depositOption == DepositOption.UnstakeTcy && selectedToken.ticker == "TCY") {
                // Convert the unstakable amount string to BigInteger for comparison
                val unstakableAmount = state.value.unstakableAmount
                    ?.toBigDecimalOrNull()
                    ?.movePointRight(selectedToken.decimal)
                    ?.toBigInteger() ?: BigInteger.ZERO

                if (tokenAmountInt <= unstakableAmount) {
                    // Amount is valid for unstaking
                    return tokenAmountInt
                }
            }

            // For all other operations, or if the unstakable check failed
            throw InvalidTransactionDataException(
                UiText.StringResource(R.string.send_error_insufficient_balance)
            )
        }

        if (nativeTokenValue < gas.value) {
            throw InvalidTransactionDataException(
                UiText.FormattedText(
                    R.string.insufficient_native_token,
                    listOf(nativeTokenAccount.token.ticker)
                )
            )
        }

        return tokenAmountInt
    }

    private fun showError(text: UiText) {
        state.update { it.copy(errorText = text) }
    }

    private fun validateCustomMemo(memo: String): UiText? = if (memo.isBlank()) {
        UiText.StringResource(R.string.dialog_default_error_title)
    } else {
        null
    }

    private fun validateDstAddress(dstAddress: String): UiText? {
        val chain =
            chain ?: return UiText.StringResource(R.string.dialog_default_error_title)
        if (dstAddress.isBlank() || !chainAccountAddressRepository.isValid(
                chain,
                dstAddress
            )
        )
            return UiText.StringResource(R.string.send_error_no_address)
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

    private fun formatUnstakableTcyAmount(unstakableAmount: String?): String? {
        if (unstakableAmount.isNullOrEmpty()) return null
        return try {
            val amount = unstakableAmount.toBigDecimalOrNull() ?: return null

            // TCY has 8 decimal places, so divide by 10^8 to get the human-readable amount
            val humanReadableAmount = amount.movePointLeft(8)

            // Use the same decimal formatting as the rest of the app
            val decimalFormat = DecimalFormat(
                "#,###.########", // 8 decimal places max, consistent with app standard
                DecimalFormatSymbols(Locale.getDefault())
            )

            // Format and strip trailing zeros
            decimalFormat.format(humanReadableAmount)
        } catch (e: Exception) {
            // Failed to format unstakable TCY amount
            Timber.e("Error formatting unstakable TCY amount: ${e.message}")
            null
        }
    }

    fun validateAssets() {
        val assets = assetsFieldState.text.toString()
        state.update {
            it.copy(
                assetsError = if (!isAssetCharsValid(assets))
                    UiText.StringResource(R.string.deposit_error_invalid_assets)
                else null
            )
        }
    }

    fun validateLpUnits() {
        val lpUnits = lpUnitsFieldState.text.toString()
        state.update {
            it.copy(
                lpUnitsError = if (!isLpUnitCharsValid(lpUnits))
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
                gasLimit = if (chain?.standard == TokenStandard.EVM) {
                    (specific.blockChainSpecific as BlockChainSpecific.Ethereum).gasLimit
                } else {
                    BigInteger.valueOf(1)
                },
                gasFee = gasFee,
                selectedToken = selectedToken,
            )
        )
    }

    private fun isLpUnitCharsValid(lpUnits: String) =
        lpUnits.toIntOrNull() != null &&
                lpUnits.all { it.isDigit() } &&
                lpUnits.toInt() > 0

    @Deprecated("Use Ruji Staking through DeFi Tab")
    private suspend fun fetchRujiStakeBalances(address: String): RujiStakeBalances {
        if (rujiStakeBalances.value != null) {
            return rujiStakeBalances.value!!
        }

        val balances = withContext(Dispatchers.IO) {
            runCatching { thorChainApi.getRujiStakeBalance(address) }.getOrDefault(RujiStakeBalances())
        }

        rujiStakeBalances.update { balances }

        return balances
    }

    private suspend fun handleRujiDepositOption(depositOption: DepositOption) {
        val rujiToken = Coins.ThorChain.RUJI
        state.update {
            it.copy(selectedToken = rujiToken, unstakableAmount = "Loading...")
        }
        val addressValue = address.value?.address

        if (addressValue != null) {
            try {
                val balances = fetchRujiStakeBalances(addressValue)

                when (depositOption) {
                    DepositOption.UnstakeRuji -> {
                        val formattedAmount =
                            CoinType.THORCHAIN.toValue(balances.stakeAmount).toPlainString()
                        state.update {
                            it.copy(unstakableAmount = formattedAmount + " ${rujiToken.ticker}")
                        }
                    }

                    DepositOption.WithdrawRujiRewards -> {
                        val formattedAmount =
                            CoinType.THORCHAIN.toValue(balances.rewardsAmount)
                                .toPlainString()
                        val rewardsTicker = balances.rewardsTicker
                        state.update {
                            it.copy(rewardsAmount = "$formattedAmount $rewardsTicker")
                        }
                        if (balances.rewardsAmount > BigInteger.ZERO) {
                            rewardsAmountFieldState.setTextAndPlaceCursorAtEnd(
                                formattedAmount
                            )
                        }
                    }

                    else -> error("Deposit type not supported for RUJI: $depositOption")
                }
            } catch (e: Exception) {
                Timber.e(e)
                state.update {
                    it.copy(unstakableAmount = null, rewardsAmount = null)
                }
            }
        }
    }


    private fun findCoin(chain: Chain, ticker: String?) =
        Coins.coins[chain]?.find { it.ticker.equals(ticker, ignoreCase = true) }


    fun onAutoCompoundTcyStake(isChecked: Boolean) {
        isAutoCompoundTcyStake = isChecked
    }

    fun onAutoCompoundTcyUnStake(isChecked: Boolean) {
        isAutoCompoundTcyUnStake = isChecked
    }

    fun onSelectSecureAsset(asset: TokenWithdrawSecureAsset) {
        val balance = asset.tokenValue?.let(mapTokenValueToStringWithUnit)
        state.update {
            it.copy(
                selectedSecuredAsset = asset,
                balance = balance?.asUiText() ?: UiText.Empty
            )
        }
    }


    @kotlin.ExperimentalStdlibApi
    fun selectUtxosIfNeeded(
        chain: Chain,
        specific: BlockChainSpecificAndUtxo
    ): BlockChainSpecificAndUtxo {
        specific.blockChainSpecific as? BlockChainSpecific.UTXO ?: return specific

        val updatedUtxo = planBtc.value?.utxosOrBuilderList?.map { planUtxo ->
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
                    listOf(
                        formattedMinAmount,
                        symbol,
                        name
                    )
                )
            )
        }
}
    suspend fun getBitcoinTransactionPlan(
        vaultId: String,
        selectedToken: Coin,
        dstAddress: String,
        tokenAmountInt: BigInteger,
        specific: BlockChainSpecificAndUtxo,
        memo: String?,
    ): Bitcoin.TransactionPlan {
        val vault = vaultRepository.get(vaultId) ?: error("Can't calculate plan fees")

        val keysignPayload = KeysignPayload(
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

}

internal data class TokenMergeInfo(
    val ticker: String,
    val contract: String,
) {

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
        val EMPTY = TokenWithdrawSecureAsset(
            ticker = "Select Asset",
            contract = "",
            coin = Coin.EMPTY,
            tokenValue = null
        )
    }
}

private val tokensToMerge = listOf(
    TokenMergeInfo(
        ticker = "KUJI",
        contract = "thor14hj2tavq8fpesdwxxcu44rty3hh90vhujrvcmstl4zr3txmfvw9s3p2nzy"
    ),
    TokenMergeInfo(
        ticker = "rKUJI",
        contract = "thor1yyca08xqdgvjz0psg56z67ejh9xms6l436u8y58m82npdqqhmmtqrsjrgh"
    ),
    TokenMergeInfo(
        ticker = "FUZN",
        contract = "thor1suhgf5svhu4usrurvxzlgn54ksxmn8gljarjtxqnapv8kjnp4nrsw5xx2d"
    ),
    TokenMergeInfo(
        ticker = "NSTK",
        contract = "thor1cnuw3f076wgdyahssdkd0g3nr96ckq8cwa2mh029fn5mgf2fmcmsmam5ck"
    ),
    TokenMergeInfo(
        ticker = "WINK",
        contract = "thor1yw4xvtc43me9scqfr2jr2gzvcxd3a9y4eq7gaukreugw2yd2f8tsz3392y"
    ),
    TokenMergeInfo(
        ticker = "LVN",
        contract = "thor1ltd0maxmte3xf4zshta9j5djrq9cl692ctsp9u5q0p9wss0f5lms7us4yf"
    ),
)

private const val DEFAULT_SLIPPAGE = "1.0"