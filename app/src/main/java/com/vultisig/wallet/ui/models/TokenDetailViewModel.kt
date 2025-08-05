package com.vultisig.wallet.ui.models

import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.IsSwapSupported
import com.vultisig.wallet.data.models.Tokens
import com.vultisig.wallet.data.models.getCoinLogo
import com.vultisig.wallet.data.models.isDepositSupported
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
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
    val isBalanceVisible: Boolean = true,
)

@HiltViewModel
internal class TokenDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,
    private val accountsRepository: AccountsRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
) : ViewModel() {
    private val chainRaw: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_CHAIN_ID))
    private val vaultId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID))
    private val tokenId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_TOKEN_ID))
    private val mergedBalance: String =
        savedStateHandle.get<String>(Destination.ARG_MERGE_ID) ?: "0"

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

    private fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            updateRefreshing(true)

            val chain = requireNotNull(Chain.fromRaw(chainRaw))

            accountsRepository.loadAddress(
                vaultId = vaultId,
                chain = chain,
            ).catch {
                // TODO handle error
                updateRefreshing(false)
                Timber.e(it)
            }.onEach { address ->
                val token = address.accounts
                    .first { it.token.id == tokenId }
                    .let { account ->
                        val token = account.token
                        ChainTokenUiModel(
                            id = token.id,
                            name = token.ticker,
                            balance = account.tokenValue
                                ?.let(mapTokenValueToDecimalUiString)
                                ?: "",
                            fiatBalance = account.fiatValue
                                ?.let { fiatValueToStringMapper(it) },
                            tokenLogo = Tokens.getCoinLogo(token.logo),
                            chainLogo = chain.logo,
                            mergeBalance = mergedBalance,
                        )
                    }

                uiState.update {
                    it.copy(
                        token = token,
                        canDeposit = chain.isDepositSupported,
                        canSwap = chain.IsSwapSupported,
                    )
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