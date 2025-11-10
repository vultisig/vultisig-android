package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.FiatValue
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.models.coinType
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.models.settings.AppCurrency
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.chaintokens.GetChainTokensUseCase
import com.vultisig.wallet.data.utils.symbol
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.Route.SelectNetwork.Filters
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.math.BigDecimal
import javax.inject.Inject
import kotlin.collections.map


internal data class SelectNetworkUiModel(
    val selectedNetwork: Chain = Chain.ThorChain,
    val networks: List<NetworkUiModel> = emptyList(),
)


internal data class SelectNetworkPopupSharedUiModel(
    val networks: List<NetworkUiModel> = emptyList(),
    val assets: List<AssetUiModel> = emptyList(),
    val isLongPressActive: Boolean = false,
    val currentDragPosition: Offset? = null,
    val vaultId: VaultId = "",
)


@HiltViewModel
internal class SelectNetworkPopupSharedViewModel @Inject constructor(
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val navigator: Navigator<Destination>,
    private val enableTokenUseCase: EnableTokenUseCase,
    private val getChainTokens: GetChainTokensUseCase,
    private val accountRepository: AccountsRepository,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
) : ViewModel() {


    val uiState = MutableStateFlow(SelectNetworkPopupSharedUiModel())
    lateinit var networkArgs: Route.Send.SelectNetworkPopup
    lateinit var assetArgs: Route.Send.SelectAssetPopup


    fun loadNetworkData() {
        val vaultId = networkArgs.vaultId
        val selectedNetwork = Chain.fromRaw(networkArgs.selectedNetworkId)

        vaultRepository.getEnabledChains(vaultId)
            .onEach { chains ->
                val networks = chains
                    .filter { chains ->
                        when (networkArgs.filters) {
                            Filters.SwapAvailable -> chains.isSwapSupported
                            Filters.DisableNetworkSelection -> chains.id == selectedNetwork.id
                            Filters.None -> true
                        }
                    }.map { chain ->
                        NetworkUiModel(
                            chain = chain,
                            logo = chain.logo,
                            title = chain.raw,
                            value = ""
                        )
                    }

                uiState.update {
                    it.copy(
                        networks = networks,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun loadAssetsData() {
        val filter = assetArgs.networkFilters
        val vaultId = assetArgs.vaultId
        val preselectedChainId = assetArgs.preselectedNetworkId
        val preselectedChain = Chain.fromRaw(preselectedChainId)

            viewModelScope.launch {
                val vault = vaultRepository.get(vaultId) ?: return@launch
                getChainTokens(preselectedChain, vault)
                    .catch { Timber.e(it) }
                    .map { coinList ->
                        coinList
                            .filter { !it.isNativeToken }
                            .map { coin ->
                                AssetUiModel(
                                    token = coin,
                                    logo = getCoinLogo(coin.logo),
                                    title = coin.ticker,
                                    subtitle = coin.chain.raw,
                                    amount = "0",
                                    value = "0",
                                    isDisabled = true,
                                )
                            }
                    }
                    .combine(
                        accountRepository.loadAddress(vaultId, preselectedChain)
                    ) { allTokens, account ->
                        val filteredAssets = account
                            .accounts
                            .asSequence()
                            .sortedWith(compareByDescending<Account> { it.token.isNativeToken }.thenBy { it.token.ticker })
                            .toList()
                            .map {
                                AssetUiModel(
                                    token = it.token,
                                    logo = getCoinLogo(it.token.logo),
                                    title = it.token.ticker,
                                    subtitle = it.token.chain.raw,
                                    amount = it.tokenValue?.let(mapTokenValueToDecimalUiString)
                                        ?: "0",
                                    value = it.fiatValue?.let { fiatValueToString.invoke(it) }
                                        ?: "0",
                                )
                            }

                        val filteredTokenIds = filteredAssets.map { it.token.id }.toSet()
                        val additionalAssets =
                            allTokens.filter {
                                it.token.id !in filteredTokenIds
                            }
                        uiState.update {
                            it.copy(assets = filteredAssets + additionalAssets)
                        }
                        println("updated to ${uiState.value.assets}")
                    }
                    .launchIn(this)
            }

    }


    fun initNetworks(args: Route.Send.SelectNetworkPopup) {
        networkArgs = args
        loadNetworkData()
    }

    fun initAssets(args: Route.Send.SelectAssetPopup) {
        assetArgs = args
        loadAssetsData()
    }


    fun onNetworkSelected(networkUiModel: NetworkUiModel) {
        viewModelScope.launch {
            requestResultRepository.respond(networkArgs.requestId, networkUiModel.chain)
            navigator.back()
        }
    }

    fun onAssetSelected(asset: AssetUiModel) {
        viewModelScope.launch {
            val isDisabled = asset.isDisabled
            if (isDisabled) {
                enableTokenUseCase(networkArgs.vaultId, asset.token)
            }
            val callbackAsset = AssetSelected(asset.token, isDisabled)
            requestResultRepository.respond(assetArgs.requestId, callbackAsset)
            navigator.navigate(Destination.Back)
        }
    }

    fun onNetworkDragStart(position: Offset) {
        uiState.update {
            it.copy(
                isLongPressActive = true,
                currentDragPosition = position
            )
        }
    }

    fun onNetworkDrag(position: Offset) {
        uiState.update {
            it.copy(
                currentDragPosition = position
            )
        }
    }

    fun onNetworkDragEnd() {
        uiState.update {
            it.copy(
                isLongPressActive = false,
                currentDragPosition = null
            )
        }
    }
}

data class NetworkUiModel(
    val chain: Chain,
    val logo: ImageModel,
    val title: String,
    val value: String? = null,
)

@HiltViewModel
internal class SelectNetworkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val accountRepository: AccountsRepository,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.SelectNetwork>()

    private val vaultId = args.vaultId
    private val selectedNetwork = Chain.fromRaw(args.selectedNetworkId)

    val searchFieldState = TextFieldState()

    val state = MutableStateFlow(
        SelectNetworkUiModel(
            selectedNetwork = selectedNetwork,
        )
    )

    init {
        collectSearchResults()
    }

    fun selectNetwork(model: NetworkUiModel) {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, model.chain)
            navigator.navigate(Destination.Back)
        }
    }

    fun back() {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, selectedNetwork)
            navigator.navigate(Destination.Back)
        }
    }

    private fun collectSearchResults() {
        combine(
            vaultRepository.getEnabledChains(vaultId),
            searchFieldState.textAsFlow().map { it.toString() },
            loadAddressesWithBalances(),
        ) { chains, query, chainBalances ->
            val filteredChains = chains
                .asSequence()
                .filter { chain ->
                    val matchesQuery = chain.raw.contains(query, ignoreCase = true) ||
                            chain.coinType.symbol.contains(query, ignoreCase = true)
                    val matchesFilter = when (args.filters) {
                        Filters.SwapAvailable -> chain.isSwapSupported
                        Filters.DisableNetworkSelection -> chain.id == selectedNetwork.id
                        Filters.None -> true
                    }
                    matchesQuery && matchesFilter
                }
                .sortedWith(compareBy { it.raw })
                .map { chain ->
                    NetworkUiModel(
                        chain = chain,
                        logo = chain.logo,
                        title = chain.raw,
                        value = chainBalances[chain] ?: ""
                    )
                }
                .toList()

            this.state.update {
                it.copy(networks = filteredChains)
            }
        }.launchIn(viewModelScope)
    }

    private fun loadAddressesWithBalances(): Flow<Map<Chain, String>> {
        return accountRepository.loadCachedAddresses(vaultId = vaultId)
            .catch {
                Timber.e(it)
                emit(emptyList())
            }
            .map { addresses ->
                coroutineScope {
                    addresses.map { address ->
                        async {
                            val totalFiatValue = address.accounts.calculateAccountsTotalFiatValue()
                                ?: FiatValue(BigDecimal.ZERO, AppCurrency.USD.ticker)
                            val formattedValue = fiatValueToStringMapper(totalFiatValue)
                            address.chain to formattedValue
                        }
                    }.awaitAll().associate { it.first to it.second }
                }
            }
    }
}