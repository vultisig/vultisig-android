@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalUuidApi::class)

package com.vultisig.wallet.ui.screens.select

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
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
)


@HiltViewModel
internal class SelectAssetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val fiatValueToString: FiatValueToStringMapper,

    private val accountRepository: AccountsRepository,
    private val requestResultRepository: RequestResultRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.SelectAsset>()

    private val vaultId = args.vaultId

    val searchFieldState = TextFieldState()

    val state = MutableStateFlow(
        SelectAssetUiModel(
            selectedChain = Chain.fromRaw(args.preselectedNetworkId),
        )
    )

    init {
        collectSearchResults()
    }

    fun selectAsset(asset: AssetUiModel) {
        viewModelScope.launch {
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

    private fun collectSearchResults() {
        combine(
            state
                .map {
                    it.selectedChain
                }
                .distinctUntilChanged()
                .flatMapConcat { selectedChain ->
                    accountRepository.loadAddress(vaultId, selectedChain)
                }
                .catch {
                    Timber.e(it)
                },
            searchFieldState.textAsFlow()
                .map { it.toString() },
        ) { account, query ->
            val filteredAssets = account
                .accounts
                .asSequence()
                .filter { it.token.id.contains(query, ignoreCase = true) }
                .sortedWith(compareBy { it.token.ticker })
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

            this.state.update {
                it.copy(assets = filteredAssets)
            }
        }.launchIn(viewModelScope)
    }

}