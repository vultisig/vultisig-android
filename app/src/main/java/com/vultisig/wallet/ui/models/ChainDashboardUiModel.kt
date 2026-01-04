package com.vultisig.wallet.ui.models

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ChainId
import com.vultisig.wallet.data.models.CryptoConnectionType
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.ChainDashboardBottomBarVisibilityRepository
import com.vultisig.wallet.data.repositories.CryptoConnectionTypeRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.ChainDashboardRoute
import com.vultisig.wallet.ui.navigation.ChainDashboardRouteNavType
import com.vultisig.wallet.ui.screens.v2.home.components.BOTH_CRYPTO_CONNECTION_TYPES
import com.vultisig.wallet.ui.screens.v2.home.components.ONLY_WALLET
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.reflect.typeOf

data class ChainDashboardUiModel(
    val isBottomBarVisible: Boolean = true,
    val cryptoConnectionType: CryptoConnectionType = CryptoConnectionType.Wallet,
    val route: ChainDashboardRoute? = null,
    val availableCryptoTypes: List<CryptoConnectionType> = BOTH_CRYPTO_CONNECTION_TYPES,
)

@HiltViewModel
internal class ChainDashboardViewModel @Inject constructor(
    val savedStateHandle: SavedStateHandle,
    private val cryptoConnectionTypeRepository: CryptoConnectionTypeRepository,
    private val bottomBarVisibility: ChainDashboardBottomBarVisibilityRepository,
    private val navigator: Navigator<Destination>,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.ChainDashboard>(
        typeMap = mapOf(
            typeOf<ChainDashboardRoute>() to ChainDashboardRouteNavType
        )
    )
    private  lateinit var vaultId: VaultId
    private var chainId: ChainId? = null

    val uiState = MutableStateFlow(
        ChainDashboardUiModel()
    )

    init {
        initData()
        initAvailableCryptoTypes()
        collectBottomBarVisibility()
        collectActiveRoute()
    }

    private fun initData() {
        when (args.route) {
            is ChainDashboardRoute.PositionCircle -> {
                vaultId = args.route.vaultId
                chainId = Chain.Ethereum.id
            }

            is ChainDashboardRoute.PositionTokens -> {
                vaultId = args.route.vaultId
                chainId = Chain.ThorChain.id
            }

            is ChainDashboardRoute.Wallet -> {
                vaultId = args.route.vaultId
                chainId = args.route.chainId
            }

        }
    }

    private fun initAvailableCryptoTypes() {
        val availableCryptoTypes =
            if (chainId in listOf(Chain.ThorChain.id, Chain.Ethereum.id))
                BOTH_CRYPTO_CONNECTION_TYPES
            else ONLY_WALLET

        uiState.update {
            it.copy(
                availableCryptoTypes = availableCryptoTypes,
            )
        }
    }


    private fun collectBottomBarVisibility() {
        bottomBarVisibility
            .getBottomBarVisibility()
            .onEach { isVisible ->
                uiState.update { state ->
                    state.copy(isBottomBarVisible = isVisible)
                }
            }
            .launchIn(viewModelScope)
    }

    private fun collectActiveRoute() {
        cryptoConnectionTypeRepository
            .activeCryptoConnectionFlow
            .onEach { type ->
                val activeRoute = when (type) {
                    CryptoConnectionType.Wallet -> ChainDashboardRoute.Wallet(
                        vaultId = vaultId,
                        chainId = requireNotNull(chainId)
                    )

                    CryptoConnectionType.Defi -> {
                        if (chainId == Chain.ThorChain.id) {
                            ChainDashboardRoute.PositionTokens(vaultId = vaultId)
                        } else {
                            ChainDashboardRoute.PositionCircle(vaultId = vaultId)
                        }
                    }
                }
                uiState.update { state ->
                    state.copy(
                        route = activeRoute,
                        cryptoConnectionType = type,
                    )
                }
            }
            .launchIn(viewModelScope)
    }

    fun updateCryptoConnectionType(cryptoConnectionType: CryptoConnectionType) {
        cryptoConnectionTypeRepository.setActiveCryptoConnection(cryptoConnectionType)
    }

    fun openCamera(){
        viewModelScope.launch {
            navigator.route(Route.ScanQr(vaultId = vaultId))
        }
    }
}