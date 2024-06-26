package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.data.repositories.TokenRepository
import com.vultisig.wallet.models.Chain
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
data class ChainTokensUiModel(
    val chainName: String = "",
    val chainAddress: String = "",
    @DrawableRes val chainLogo: Int? = null,
    val totalBalance: String? = null,
    val explorerURL: String = "",
    val tokens: List<ChainTokenUiModel> = emptyList(),
    val canDeposit: Boolean = true,
    val canSwap: Boolean = true,
    val canSelectTokens: Boolean = false,
)

@Immutable
data class ChainTokenUiModel(
    val name: String,
    val balance: String?,
    val fiatBalance: String?,
    val tokenLogo: ImageModel,
    @DrawableRes val chainLogo: Int,
)

@HiltViewModel
internal class ChainTokensViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val mapTokenValueToDecimalUiString: TokenValueToDecimalUiStringMapper,

    private val explorerLinkRepository: ExplorerLinkRepository,
    private val accountsRepository: AccountsRepository,
    private val tokensRepository: TokenRepository,
) : ViewModel() {
    private val chainRaw: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_CHAIN_ID))
    private val vaultId: String =
        requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID))

    val uiState = MutableStateFlow(ChainTokensUiModel())

    private var loadDataJob: Job? = null

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

    private fun loadData() {
        loadDataJob?.cancel()
        loadDataJob = viewModelScope.launch {
            val chain = requireNotNull(Chain.entries.find { it.raw == chainRaw })
            accountsRepository.loadAddress(
                vaultId = vaultId,
                chain = chain,
            ).catch {
                // TODO handle error
                Timber.e(it)
            }.collect { address ->
                val totalFiatValue = address.accounts
                    .calculateAccountsTotalFiatValue()

                val tokens = address.accounts
                    .sortedWith(
                        compareBy({ !it.token.isNativeToken },
                            { (it.fiatValue?.value ?: it.tokenValue?.decimal)?.unaryMinus() })
                    )
                    .map { account ->
                        ChainTokenUiModel(
                            name = account.token.ticker,
                            balance = account.tokenValue
                                ?.let(mapTokenValueToDecimalUiString)
                                ?: "",
                            fiatBalance = account.fiatValue
                                ?.let(fiatValueToStringMapper::map),
                            tokenLogo = Coins.getCoinLogo(account.token.logo),
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
                        tokens = tokens,
                        explorerURL = explorerUrl,
                        totalBalance = totalBalance,
                        canDeposit = chain.isDepositSupported,
                        canSwap = chain.IsSwapSupported,
                        canSelectTokens = chain.canSelectTokens,
                    )
                }
            }
        }
    }

}