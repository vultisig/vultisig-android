package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.BalanceVisibilityRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.usecases.EnableTokenUseCase
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coin
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.IsSwapSupported
import com.vultisig.wallet.models.canSelectTokens
import com.vultisig.wallet.models.isDepositSupported
import com.vultisig.wallet.models.logo
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.models.mappers.TokenValueToDecimalUiStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@Immutable
internal data class ChainTokensUiModel(
    val isRefreshing: Boolean = false,
    val chainName: String = "",
    val chainAddress: String = "",
    @DrawableRes val chainLogo: Int? = null,
    val totalBalance: String? = null,
    val explorerURL: String = "",
    val tokens: List<ChainTokenUiModel> = emptyList(),
    val canDeposit: Boolean = true,
    val canSwap: Boolean = true,
    val canSelectTokens: Boolean = false,
    val isBalanceVisible: Boolean = true,
    val isBuyWeweVisible: Boolean = false,
)

@Immutable
internal data class ChainTokenUiModel(
    val id: String = "",
    val name: String = "",
    val balance: String? = null,
    val fiatBalance: String? = null,
    val tokenLogo: ImageModel = "",
    @DrawableRes val chainLogo: Int? = null,
)

@HiltViewModel
internal class ChainTokensViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,

    private val explorerLinkRepository: ExplorerLinkRepository,
    private val accountsRepository: AccountsRepository,
    private val balanceVisibilityRepository: BalanceVisibilityRepository,
    private val enableTokenUseCase: EnableTokenUseCase,
) : ViewModel() {
    private val tokens = MutableStateFlow(emptyList<Coin>())
    private val chainRaw: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_CHAIN_ID))
    private val vaultId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID))

    val uiState = MutableStateFlow(ChainTokensUiModel())

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
            navigator.navigate(
                Destination.Send(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun swap() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.Swap(
                    vaultId = vaultId,
                    chainId = chainRaw,
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

    fun selectTokens() {
        viewModelScope.launch {
            navigator.navigate(
                Destination.SelectTokens(
                    vaultId = vaultId,
                    chainId = chainRaw,
                )
            )
        }
    }

    fun openToken(model: ChainTokenUiModel) {
        viewModelScope.launch {
            navigator.navigate(
                Destination.TokenDetail(
                    vaultId = vaultId,
                    chainId = chainRaw,
                    tokenId = model.id,
                )
            )
        }
    }

    fun buyWewe() {
        viewModelScope.launch {
            if(!tokens.value.contains(Coins.wewe)){
                enableTokenUseCase(vaultId, Coins.wewe)
            }
            navigator.navigate(
                Destination.Swap(
                    vaultId = vaultId,
                    chainId = chainRaw,
                    dstTokenId = Coins.wewe.id,
                )
            )
        }
    }

    private fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            uiState.update { it.copy(isRefreshing = true) }
            val chain = requireNotNull(Chain.entries.find { it.raw == chainRaw })
            accountsRepository.loadAddress(
                vaultId = vaultId,
                chain = chain,
            ).catch {
                // TODO handle error
                Timber.e(it)
            }.collect { address ->
                uiState.update { it.copy(isRefreshing = false) }

                val totalFiatValue = address.accounts
                    .calculateAccountsTotalFiatValue()

                val accounts = address.accounts
                    .sortedWith(
                        compareBy({ !it.token.isNativeToken },
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
                            ?.let(mapTokenValueToDecimalUiString)
                            ?: "",
                        fiatBalance = account.fiatValue
                            ?.let(fiatValueToStringMapper::map),
                        tokenLogo = Coins.getCoinLogo(token.logo),
                        chainLogo = chain.logo,
                    )
                }

                val accountAddress = address.address
                val explorerUrl = explorerLinkRepository
                    .getAddressLink(chain, accountAddress)
                val totalBalance = totalFiatValue
                    ?.let(fiatValueToStringMapper::map)


                uiState.update {
                    it.copy(
                        chainName = chainRaw,
                        chainAddress = accountAddress,
                        chainLogo = chain.logo,
                        tokens = uiTokens,
                        explorerURL = explorerUrl,
                        totalBalance = totalBalance,
                        canDeposit = chain.isDepositSupported,
                        canSwap = chain.IsSwapSupported,
                        canSelectTokens = chain.canSelectTokens,
                        isBuyWeweVisible = chain == Chain.Base
                    )
                }
            }
        }
    }

}