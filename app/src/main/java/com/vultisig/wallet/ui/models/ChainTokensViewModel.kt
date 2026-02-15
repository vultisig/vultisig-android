package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.MergeAccount
import com.vultisig.wallet.data.api.models.ResourceUsage
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.canSelectTokens
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isBuySupported
import com.vultisig.wallet.data.models.isDepositSupported
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.monoToneLogo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ChainDashboardBottomBarVisibilityRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.DiscoverTokenUseCase
import com.vultisig.wallet.ui.models.TokenSelectionViewModel.Companion.REFRESH_TOKEN_DATA
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject

@Immutable
internal data class ChainTokensUiModel(
    val isRefreshing: Boolean = false,
    val isSearchMode: Boolean = false,
    val chainName: String = "",
    val chainAddress: String = "",
    @DrawableRes val chainLogo: Int? = null,
    val totalBalance: String? = null,
    val explorerURL: String = "",
    val tokens: List<ChainTokenUiModel> = emptyList(),
    val canDeposit: Boolean = true,
    val canSwap: Boolean = true,
    val canBuy: Boolean = false,
    val canSelectTokens: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val searchTextFieldState: TextFieldState = TextFieldState(),
    val scanQrUiModel: ScanQrUiModel = ScanQrUiModel(),
    val tronResourceStats: ResourceUsage? = null,
)

@Immutable
internal data class ChainTokenUiModel(
    val id: String = "",
    val name: String = "",
    val balance: String? = null,
    val fiatBalance: String? = null,
    val tokenLogo: ImageModel = "",
    val price: String? = null,
    @DrawableRes val chainLogo: Int? = null,
    @DrawableRes val monotoneChainLogo: Int? = null,
    val mergeBalance: String? = null,
    val network: String = "",
)

@HiltViewModel
internal class ChainTokensViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToStringWithUnitMapper: TokenValueToStringWithUnitMapper,
    private val discoverTokenUseCase: DiscoverTokenUseCase,

    private val explorerLinkRepository: ExplorerLinkRepository,
    private val accountsRepository: AccountsRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val bottomBarVisibility: ChainDashboardBottomBarVisibilityRepository,
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val balanceRepository: BalanceRepository,

    ) : ViewModel() {
    private val tokens = MutableStateFlow(emptyList<Coin>())

    private lateinit var chainRaw: String
    private lateinit var vaultId: String
    private var currentVault: Vault? = null

    val uiState = MutableStateFlow(ChainTokensUiModel())

    private var loadDataJob: Job? = null

    private fun updateBalanceVisibility() {
        viewModelScope.launch {
            val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
            uiState.update {
                it.copy(isBalanceVisible = isBalanceVisible)
            }
        }
    }

    fun initData(
        vaultId: String,
        chainId: String
    ) {
        this.vaultId = vaultId
        this.chainRaw = chainId
        updateBalanceVisibility()
        loadData(isRefresh = false)
    }

    fun refresh() {
        updateBalanceVisibility()
        loadData(isRefresh = true)
    }

    fun send() {
        viewModelScope.launch {
            navigator.route(
                Route.Send(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun swap() {
        viewModelScope.launch {
            navigator.route(
                Route.Swap(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun deposit() {
        viewModelScope.launch {
            navigator.route(
                Route.Deposit(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun buy() {
        viewModelScope.launch {
            navigator.route(
                Route.OnRamp(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun selectTokens() {
        viewModelScope.launch {
            navigator.route(
                Route.SelectTokens(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
            requestResultRepository.request<Unit>(REFRESH_TOKEN_DATA)
            loadData(isRefresh = true)
        }
    }

    fun openToken(model: ChainTokenUiModel) {
        viewModelScope.launch {
            navigator.route(
                Route.TokenDetail(
                    vaultId = vaultId,
                    chainId = chainRaw,
                    tokenId = model.id,
                    mergeId = model.mergeBalance ?: "0",
                )
            )
        }
    }

    private fun loadData(
        isRefresh: Boolean
    ) {
        discoverTokenUseCase(
            vaultId,
            chainRaw
        )

        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            if(isRefresh) {
                updateRefreshing(true)
            }
            val chain = requireNotNull(Chain.entries.find { it.raw == chainRaw })

            val addressDataSource = if (isRefresh) {
                accountsRepository.loadAddress(
                    vaultId = vaultId,
                    chain = chain,
                )
            } else {
                accountsRepository.loadCachedAddress(
                    vaultId = vaultId,
                    chain = chain,
                )
            }

            currentVault = vaultRepository.get(vaultId)
                ?: error("No vault with $vaultId")
            collectTronResourceStats(chain)
            addressDataSource
                .onEach {
                    if (isRefresh) {
                        updateRefreshing(it.accounts.hasNullAccount())
                    }
                }
                .combine(fetchMergeBalanceFlow(chain)) { address, mergeBalance ->
                    address to mergeBalance
                }
                .catch {
                    if(isRefresh) {
                        updateRefreshing(false)
                    }
                    Timber.e(it)
                }
                .combine(
                    uiState.value.searchTextFieldState.textAsFlow()
                ) { (address, mergeBalances), searchQuery ->
                    val totalFiatValue = address.accounts
                        .calculateAccountsTotalFiatValue()

                    val accounts = address.accounts
                        .sortedWith(
                            compareBy(
                                { !it.token.isNativeToken },
                                { (it.fiatValue?.value ?: it.tokenValue?.decimal)?.unaryMinus() })
                        )

                    val tokensFromAccounts = accounts.map { it.token }
                    tokens.update { it + tokensFromAccounts }
                    val uiTokens = accounts.map { account ->
                        val token = account.token
                        ChainTokenUiModel(
                            id = token.id,
                            name = token.ticker,
                            balance = account.tokenValue
                                ?.let(mapTokenValueToStringWithUnitMapper)
                                ?: "",
                            fiatBalance = account.fiatValue
                                ?.let { fiatValueToStringMapper(it) },
                            tokenLogo = getCoinLogo(token.logo),
                            chainLogo = chain.logo,
                            monotoneChainLogo = chain.monoToneLogo,
                            mergeBalance = mergeBalances.findMergeBalance(token).toString(),
                            price = account.price?.let { fiatValueToStringMapper(it) },
                            network = token.chain.raw,
                        )
                    }

                    val accountAddress = address.address
                    val explorerUrl = explorerLinkRepository
                        .getAddressLink(
                            chain,
                            accountAddress
                        )
                    val totalBalance = totalFiatValue
                        ?.let { fiatValueToStringMapper(it) }

                    uiState.update {
                        it.copy(
                            chainName = chainRaw,
                            chainAddress = accountAddress,
                            chainLogo = chain.logo,
                            tokens = uiTokens.filter { uiToken ->
                                searchQuery.isBlank() || uiToken.name.contains(
                                    searchQuery,
                                    ignoreCase = true
                                )
                            },
                            explorerURL = explorerUrl,
                            totalBalance = totalBalance,
                            canDeposit = chain.isDepositSupported,
                            canSwap = chain.isSwapSupported,
                            canBuy = chain.isBuySupported,
                            canSelectTokens = chain.canSelectTokens,
                        )
                    }
                }
                .collect()
        }
    }

    private fun collectTronResourceStats(chain: Chain) {
        viewModelScope.launch {
            if (chain == Chain.Tron) {
                val address = currentVault?.coins
                    ?.firstOrNull { it.chain == chain }
                    ?.address

                if (address == null) {
                    Timber.w(
                        "No TRON address for chain %s in vault %s",
                        chainRaw,
                        vaultId
                    )
                    return@launch
                }
                balanceRepository
                    .getTronResourceDataSource(address)
                    .flowOn(Dispatchers.IO)
                    .catch {
                        Timber.e(
                            it,
                            "Error fetching tron resource data for address $address"
                        )
                    }
                    .collect {
                        uiState.update { uiState ->
                            uiState.copy(
                                tronResourceStats = it
                            )
                        }
                    }
            }
        }
    }


    fun openAddressQr() {
        viewModelScope.launch {
            navigator.route(
                Route.AddressQr(
                    vaultId = vaultId,
                    address = uiState.value.chainAddress,
                    name = uiState.value.chainName,
                    logo = uiState.value.chainLogo
                )
            )
        }
    }

    fun hideSearchBar() {
        uiState.update { it.copy(isSearchMode = false) }
    }

    fun showSearchBar() {
        uiState.update { it.copy(isSearchMode = true) }
    }

    fun handleKeyboardState(isKeyboardOpen: Boolean) {
        if (isKeyboardOpen) {
            bottomBarVisibility.hideBottomBar()
        } else {
            bottomBarVisibility.showBottomBar()
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }


    private fun fetchMergeBalanceFlow(
        chain: Chain,
    ): Flow<List<MergeAccount>> = flow {
        emit(emptyList())
        emit(
            accountsRepository.fetchMergeBalance(
                chain,
                vaultId
            )
        )
    }

    private fun updateRefreshing(isRefreshing: Boolean) {
        uiState.update { it.copy(isRefreshing = isRefreshing) }
    }

    private fun List<MergeAccount>.findMergeBalance(coin: Coin): BigInteger {
        val ticker = coin.ticker.lowercase()

        val mergeBalance = this.firstOrNull {
            it.pool?.mergeAsset?.metadata?.symbol.equals(
                ticker,
                true
            )
        }?.shares?.toBigIntegerOrNull() ?: BigInteger.ZERO

        return mergeBalance
    }

    private fun List<Account>.hasNullAccount() = any {
        it.tokenValue == null || it.fiatValue == null
    }
}