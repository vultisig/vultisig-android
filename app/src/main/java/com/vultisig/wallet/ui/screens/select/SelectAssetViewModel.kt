@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isLpToken
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.data.usecases.chaintokens.GetChainTokensUseCase
import com.vultisig.wallet.ui.models.NetworkUiModel
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class SelectAssetUiModel(
    val selectedChain: Chain = Chain.ThorChain,
    val chains: List<NetworkUiModel> = emptyList(),
    val assets: List<AssetUiModel> = emptyList(),
)

internal data class AssetUiModel(
    val token: Coin,
    val logo: ImageModel,
    val title: String,
    val subtitle: String,
    val amount: String,
    val value: String,
    val isDisabled: Boolean = false,
)

@HiltViewModel
internal class SelectAssetViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
    private val accountRepository: AccountsRepository,
    private val requestResultRepository: RequestResultRepository,
    private val getChainTokens: GetChainTokensUseCase,
    private val vaultRepository: VaultRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.SelectAsset>()
    private val vaultId = args.vaultId
    private val filter = args.networkFilters

    val searchFieldState = TextFieldState()

    val state =
        MutableStateFlow(
            SelectAssetUiModel(selectedChain = Chain.fromRaw(args.preselectedNetworkId))
        )

    init {
        collectAssets()
        loadAllAvailableNetworks()
    }

    private fun collectAssets() {
        state
            .map { it.selectedChain }
            .distinctUntilChanged()
            .flatMapLatest { chain ->
                val vault = vaultRepository.get(vaultId) ?: return@flatMapLatest emptyFlow()
                combine(
                    accountRepository.loadAddress(vaultId, chain).catch {
                        Timber.e(it)
                        emit(Address(chain = chain, address = "", accounts = emptyList()))
                    },
                    getChainTokens(chain, vault)
                        .catch {
                            Timber.e(it)
                            emit(emptyList())
                        }
                        .map { coinList ->
                            coinList
                                .filterNot { it.isNativeToken || it.isLpToken }
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
                        },
                    searchFieldState.textAsFlow().map { it.toString() },
                ) { account, allTokens, query ->
                    val filteredAssets =
                        account.accounts
                            .asSequence()
                            .filter { it.token.id.contains(query, ignoreCase = true) }
                            .filterNot {
                                filter == Route.SelectNetwork.Filters.SwapAvailable &&
                                    it.token.isLpToken
                            }
                            .sortedWith(
                                compareByDescending<Account> { it.token.isNativeToken }
                                    .thenBy { it.token.ticker }
                            )
                            .toList()
                            .map {
                                AssetUiModel(
                                    token = it.token,
                                    logo = getCoinLogo(it.token.logo),
                                    title = it.token.ticker,
                                    subtitle = it.token.chain.raw,
                                    amount =
                                        it.tokenValue?.let(mapTokenValueToDecimalUiString) ?: "0",
                                    value =
                                        it.fiatValue?.let { fiatValueToString.invoke(it) } ?: "0",
                                )
                            }

                    val filteredTokenIds = filteredAssets.map { it.token.id }.toSet()
                    val additionalAssets =
                        allTokens.filter {
                            it.token.id.contains(query, ignoreCase = true) &&
                                it.token.id !in filteredTokenIds
                        }

                    filteredAssets + additionalAssets
                }
            }
            .onEach { assets -> state.update { it.copy(assets = assets) } }
            .launchIn(viewModelScope)
    }

    fun selectAsset(asset: AssetUiModel) {
        viewModelScope.launch {
            val isDisabled = asset.isDisabled
            if (isDisabled) {
                enableTokenUseCase.invoke(vaultId, asset.token)
            }
            val callbackAsset = AssetSelected(asset.token, isDisabled)
            requestResultRepository.respond(args.requestId, callbackAsset)
            navigator.navigate(Destination.Back)
        }
    }

    fun selectChain(chain: Chain) {
        state.update { it.copy(selectedChain = chain) }
    }

    fun back() {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, null)
            navigator.navigate(Destination.Back)
        }
    }

    private fun loadAllAvailableNetworks() {
        viewModelScope.launch {
            val availableChains =
                vaultRepository
                    .getEnabledChains(vaultId)
                    .first()
                    .map { chain ->
                        NetworkUiModel(chain = chain, logo = chain.logo, title = chain.name)
                    }
                    .filter {
                        when (filter) {
                            Route.SelectNetwork.Filters.SwapAvailable -> it.chain.isSwapSupported
                            else -> true
                        }
                    }
                    .sortedByDescending { it.chain.id == state.value.selectedChain.id }

            state.update { it.copy(chains = availableChains) }
        }
    }
}

data class AssetSelected(val token: Coin, val isDisabled: Boolean)
