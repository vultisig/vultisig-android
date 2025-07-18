@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapConcat
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

internal data class SelectAssetUiModel(
    val selectedChain: Chain = Chain.ThorChain,
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

//TODO : Refactor current implementation for now it will only load all tokens for swap,
@HiltViewModel
internal class SelectAssetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,
    private val accountRepository: AccountsRepository,
    private val requestResultRepository: RequestResultRepository,
    private val tokenRepository: TokenRepository,
    private val vaultRepository: VaultRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.SelectAsset>()
    private val vaultId = args.vaultId
    private val filter = args.networkFilters

    val searchFieldState = TextFieldState()

    private val allTokens = MutableStateFlow(emptyList<AssetUiModel>())

    val state = MutableStateFlow(
        SelectAssetUiModel(
            selectedChain = Chain.fromRaw(args.preselectedNetworkId),
        )
    )

    init {
        loadAllAssets()
        collectSearchResults()
        observeSelectedChainChanges()
    }

    private fun observeSelectedChainChanges() {
        if (filter == Route.SelectNetwork.Filters.SwapAvailable) {
            state.map { it.selectedChain }
                .distinctUntilChanged()
                .onEach {
                    loadAllAssets()
                }
                .launchIn(viewModelScope)
        }
    }

    fun selectAsset(asset: AssetUiModel) {
        viewModelScope.launch {
            if (asset.isDisabled) {
                enableTokenUseCase.invoke(vaultId, asset.token)
            }
            requestResultRepository.respond(args.requestId, asset.token)
            navigator.navigate(Destination.Back)
        }
    }

    fun selectNetwork() {
        viewModelScope.launch {
            val requestId = Uuid.random().toString()
            navigator.route(
                Route.SelectNetwork(
                    vaultId = vaultId,
                    selectedNetworkId = state.value.selectedChain.id,
                    requestId = requestId,
                    filters = args.networkFilters,
                )
            )

            val chain: Chain = requestResultRepository.request(requestId)
                ?: return@launch

            state.update {
                it.copy(selectedChain = chain)
            }
        }
    }

    fun back() {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, null)
            navigator.navigate(Destination.Back)
        }
    }

    private fun loadAllAssets() {
        if (filter == Route.SelectNetwork.Filters.SwapAvailable) {
            viewModelScope.launch {
                val vault = vaultRepository.get(vaultId) ?: error("Can't load vault")
                tokenRepository.getChainTokens(state.value.selectedChain, vault)
                    .map { coinList ->
                        coinList
                            .filter { !it.isNativeToken }
                            .map { coin ->
                                AssetUiModel(
                                    token = coin,
                                    logo = Tokens.getCoinLogo(coin.logo),
                                    title = coin.ticker,
                                    subtitle = coin.chain.raw,
                                    amount = "0",
                                    value = "0",
                                    isDisabled = true,
                                )
                            }
                    }.collect { assets ->
                        allTokens.value = assets
                    }
            }
        }
    }

    private fun collectSearchResults() {
        combine(
            state.map { it.selectedChain }
                .distinctUntilChanged()
                .flatMapConcat { selectedChain ->
                    accountRepository.loadAddress(vaultId, selectedChain)
                }
                .catch { Timber.e(it) },
            searchFieldState.textAsFlow().map { it.toString() },
            allTokens,
        ) { account, query, allTokens ->
            val filteredAssets = account
                .accounts
                .asSequence()
                .filter { it.token.id.contains(query, ignoreCase = true) }
                .sortedWith(compareByDescending<Account> { it.token.isNativeToken }.thenBy { it.token.ticker })
                .map {
                    AssetUiModel(
                        token = it.token,
                        logo = Tokens.getCoinLogo(it.token.logo),
                        title = it.token.ticker,
                        subtitle = it.token.chain.raw,
                        amount = it.tokenValue?.let(mapTokenValueToDecimalUiString) ?: "0",
                        value = it.fiatValue?.let(fiatValueToString::map) ?: "0",
                    )
                }
                .toList()

            val filteredTokenIds = filteredAssets.map { it.token.id }.toSet()
            val additionalAssets =
                allTokens.filter {
                    it.token.id.contains(query, ignoreCase = true)
                            && it.token.id !in filteredTokenIds
                }

            state.update {
                it.copy(assets = filteredAssets + additionalAssets)
            }
        }.launchIn(viewModelScope)
    }
}