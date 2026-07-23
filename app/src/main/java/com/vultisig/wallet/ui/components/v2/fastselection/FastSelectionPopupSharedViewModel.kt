package com.vultisig.wallet.ui.components.v2.fastselection

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isLpToken
import com.vultisig.wallet.data.models.isReadOnlyAsset
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.models.NetworkUiModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.screens.select.AssetSelected
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/** Minimal UI model for the fast token picker — just enough to render an icon and label. */
internal data class FastAssetUiModel(val token: Coin, val logo: ImageModel, val title: String)

internal data class SelectNetworkPopupSharedUiModel(
    val networks: List<NetworkUiModel> = emptyList(),
    val assets: List<FastAssetUiModel> = emptyList(),
    val isLongPressActive: Boolean = false,
    val currentDragPosition: Offset? = null,
    val vaultId: VaultId = "",
)

@HiltViewModel
internal class FastSelectionPopupSharedViewModel
@Inject
constructor(
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    val uiState = MutableStateFlow(SelectNetworkPopupSharedUiModel())
    lateinit var networkArgs: Route.SelectNetworkPopup
    lateinit var assetArgs: Route.SelectAssetPopup
    private var assetLoadJob: Job? = null

    fun loadNetworkData() {
        val vaultId = networkArgs.vaultId
        val selectedNetwork = Chain.fromRaw(networkArgs.selectedNetworkId)

        vaultRepository
            .getEnabledChains(vaultId)
            .onEach { chains ->
                val networks =
                    chains
                        .filter { chains ->
                            when (networkArgs.filters) {
                                Route.SelectNetwork.Filters.SwapAvailable -> chains.isSwapSupported
                                Route.SelectNetwork.Filters.DisableNetworkSelection ->
                                    chains.id == selectedNetwork.id
                                Route.SelectNetwork.Filters.None -> true
                            }
                        }
                        .map { chain ->
                            NetworkUiModel(
                                chain = chain,
                                logo = chain.logo,
                                title = chain.raw,
                                value = "",
                            )
                        }

                uiState.update { it.copy(networks = networks) }
            }
            .launchIn(viewModelScope)
    }

    fun initNetworks(args: Route.SelectNetworkPopup) {
        networkArgs = args
        loadNetworkData()
    }

    /**
     * Loads the vault's enabled tokens for the popup's preselected chain and exposes them via
     * [uiState]. Mirrors [loadNetworkData] but operates on assets instead of chains. Reads coins
     * directly from the vault to avoid the network side-effects (price refresh, SPL discovery) that
     * `AccountsRepository.loadAddress` triggers — the popup only needs ticker and logo. Tokens are
     * sorted native-first then alphabetically by ticker so the popup order matches the full-screen
     * `SelectAssetViewModel`.
     */
    fun loadAssetData() {
        assetLoadJob?.cancel()
        uiState.update { it.copy(assets = emptyList()) }
        val vaultId = assetArgs.vaultId
        val chain = Chain.fromRaw(assetArgs.preselectedNetworkId)

        assetLoadJob =
            vaultRepository
                .getEnabledTokens(vaultId)
                .catch { Timber.e(it) }
                .onEach { tokens ->
                    val assets =
                        tokens
                            .asSequence()
                            .filter { it.chain == chain }
                            .filterNot { it.isLpToken || it.isReadOnlyAsset }
                            .sortedWith(
                                compareByDescending<Coin> { it.isNativeToken }.thenBy { it.ticker }
                            )
                            .map { token ->
                                FastAssetUiModel(
                                    token = token,
                                    logo = getCoinLogo(token.logo),
                                    title = token.ticker,
                                )
                            }
                            .toList()
                    uiState.update { it.copy(assets = assets) }
                }
                .launchIn(viewModelScope)
    }

    /** Stores the popup args and triggers async loading of the enabled tokens for the chain. */
    fun initAssets(args: Route.SelectAssetPopup) {
        assetArgs = args
        loadAssetData()
    }

    fun onNetworkSelected(networkUiModel: NetworkUiModel) {
        viewModelScope.launch {
            requestResultRepository.respond(networkArgs.requestId, networkUiModel.chain)
            navigator.back()
        }
    }

    /**
     * Responds to the awaiting `SendFormViewModel.openTokenSelectionPopup` coroutine with the
     * picked token and dismisses the dialog. Tokens shown here are already enabled in the vault, so
     * `isDisabled` is always false.
     */
    fun onAssetSelected(assetUiModel: FastAssetUiModel) {
        viewModelScope.launch {
            requestResultRepository.respond(
                assetArgs.requestId,
                AssetSelected(token = assetUiModel.token, isDisabled = false),
            )
            navigator.back()
        }
    }

    fun onDragStart(position: Offset) {
        uiState.update { it.copy(isLongPressActive = true, currentDragPosition = position) }
    }

    fun onDrag(position: Offset) {
        uiState.update { it.copy(currentDragPosition = position) }
    }

    fun onDragEnd() {
        uiState.update { it.copy(isLongPressActive = false, currentDragPosition = null) }
    }
}
