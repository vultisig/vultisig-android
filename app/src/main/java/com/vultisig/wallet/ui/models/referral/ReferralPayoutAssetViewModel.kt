package com.vultisig.wallet.ui.models.referral

import androidx.compose.foundation.text.input.TextFieldState
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.ThorChainPoolCoin
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.usecases.GetThorChainPoolAssetsUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class PayoutAssetUiModel(
    val asset: String,
    val logo: ImageModel,
    val ticker: String,
    val chain: String,
    val isSelected: Boolean = false,
)

internal data class ReferralPayoutAssetUiState(
    val assets: List<PayoutAssetUiModel> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
internal class ReferralPayoutAssetViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val requestResultRepository: RequestResultRepository,
    private val getThorChainPoolAssets: GetThorChainPoolAssetsUseCase,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.ReferralPayoutAsset>()

    private var assets: List<ThorChainPoolCoin> = emptyList()

    val searchFieldState = TextFieldState()
    val state = MutableStateFlow(ReferralPayoutAssetUiState())

    init {
        loadAssets()
        observeSearch()
    }

    private fun loadAssets() {
        viewModelScope.launch {
            try {
                assets = getThorChainPoolAssets()
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.e(t, "Failed to load THORChain pool assets")
                assets = emptyList()
            }
            state.update {
                it.copy(isLoading = false, assets = filterAssets(searchFieldState.text.toString()))
            }
        }
    }

    private fun observeSearch() {
        viewModelScope.launch {
            searchFieldState.textAsFlow().collectLatest { query ->
                state.update { it.copy(assets = filterAssets(query.toString())) }
            }
        }
    }

    private fun filterAssets(query: String): List<PayoutAssetUiModel> =
        assets
            .filter { query.isBlank() || it.coin.ticker.contains(query.trim(), ignoreCase = true) }
            .map {
                PayoutAssetUiModel(
                    asset = it.asset,
                    logo = getCoinLogo(it.coin.logo),
                    ticker = it.coin.ticker,
                    chain = it.coin.chain.raw,
                    isSelected = it.asset.equals(args.selectedAsset, ignoreCase = true),
                )
            }

    fun onAssetClick(asset: PayoutAssetUiModel) {
        viewModelScope.launch {
            val selected = assets.firstOrNull { it.asset == asset.asset } ?: return@launch
            requestResultRepository.respond(args.requestId, selected)
            navigator.navigate(Destination.Back)
        }
    }

    fun back() {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, null)
            navigator.navigate(Destination.Back)
        }
    }
}
