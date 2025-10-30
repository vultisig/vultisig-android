package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.isSwapSupported
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isBuySupported
import com.vultisig.wallet.data.models.isDepositSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToStringWithUnitMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class TokenDetailUiModel(
    val token: ChainTokenUiModel = ChainTokenUiModel(),
    val isRefreshing: Boolean = false,
    val canDeposit: Boolean = false,
    val canSwap: Boolean = false,
    val canBuy: Boolean = false,
    val isBalanceVisible: Boolean = true,
)

@HiltViewModel
internal class TokenDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToStringWithUnitMapper: TokenValueToStringWithUnitMapper,
    private val accountsRepository: AccountsRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
) : ViewModel() {

    private val tokenDetail = savedStateHandle.toRoute<Route.TokenDetail>()
    private val chainRaw: String = tokenDetail.chainId
    private val vaultId: String = tokenDetail.vaultId
    private val tokenId: String = tokenDetail.tokenId
    private val mergedBalance: String = tokenDetail.mergeId

    val uiState = MutableStateFlow(TokenDetailUiModel())

    private var loadDataJob: Job? = null

    init {
        viewModelScope.launch {
            val isBalanceVisible = balanceVisibilityRepository.getVisibility(vaultId)
            uiState.update {
                it.copy(isBalanceVisible = isBalanceVisible)
            }
        }
    }


    fun refresh() {
        loadData()
    }

    fun send() {
        viewModelScope.launch {
            navigator.route(
                Route.Send(
                    vaultId = vaultId,
                    chainId = chainRaw,
                    tokenId = tokenId,
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
                    srcTokenId = tokenId,
                )
            )
        }
    }


    fun deposit() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.Deposit(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }

    fun buy() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.OnRamp(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    private fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            updateRefreshing(true)

            val chain = requireNotNull(Chain.fromRaw(chainRaw))

            accountsRepository.loadAddress(
                vaultId = vaultId,
                chain = chain,
            ).catch {
                updateRefreshing(false)
                Timber.e(it)
            }.onEach { address ->
                address.accounts
                    .firstOrNull { it.token.id == tokenId }
                    ?.let { account ->
                        val token = account.token
                        val tokenUiModel = ChainTokenUiModel(
                            id = token.id,
                            name = token.ticker,
                            balance = account.tokenValue
                                ?.let(mapTokenValueToStringWithUnitMapper)
                                ?: "",
                            fiatBalance = account.fiatValue
                                ?.let { fiatValueToStringMapper(it) },
                            tokenLogo = getCoinLogo(token.logo),
                            chainLogo = chain.logo,
                            mergeBalance = mergedBalance,
                            price = account.price?.let { fiatValueToStringMapper(it) },
                            network = token.chain.raw,
                        )

                        uiState.update {
                            it.copy(
                                token = tokenUiModel,
                                canDeposit = chain.isDepositSupported,
                                canSwap = chain.isSwapSupported,
                                canBuy = chain.isBuySupported,
                            )
                        }
                    } ?: run {
                    updateRefreshing(false)
                }
            }.onCompletion {
                updateRefreshing(false)
            }.collect()
        }
    }

    private fun updateRefreshing(isRefreshing: Boolean) {
        uiState.update { it.copy(isRefreshing = isRefreshing) }
    }
}