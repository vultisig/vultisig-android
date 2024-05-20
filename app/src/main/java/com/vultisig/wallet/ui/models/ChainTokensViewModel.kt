package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.calculateAccountsTotalFiatValue
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.ExplorerLinkRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.IsSwapSupported
import com.vultisig.wallet.models.isDepositSupported
import com.vultisig.wallet.models.logo
import com.vultisig.wallet.ui.models.mappers.FiatValueToStringMapper
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Screen.ChainCoin.CHAIN_COIN_PARAM_CHAIN_RAW
import com.vultisig.wallet.ui.navigation.Screen.ChainCoin.CHAIN_COIN_PARAM_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class ChainTokensUiModel(
    val chainName: String = "",
    val chainAddress: String = "",
    @DrawableRes val chainLogo: Int? = null,
    val totalBalance: String = "",
    val explorerURL: String = "",
    val tokens: List<ChainTokenUiModel> = emptyList(),
    val canDeposit: Boolean = true,
    val canSwap: Boolean = true,
)

@Immutable
data class ChainTokenUiModel(
    val name: String,
    val balance: String?,
    val fiatBalance: String?,
    @DrawableRes val tokenLogo: Int,
    @DrawableRes val chainLogo: Int,
)

@HiltViewModel
internal class ChainTokensViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val fiatValueToStringMapper: FiatValueToStringMapper,

    private val explorerLinkRepository: ExplorerLinkRepository,
    private val accountsRepository: AccountsRepository,
) : ViewModel() {
    private val chainRaw: String = savedStateHandle.get<String>(CHAIN_COIN_PARAM_CHAIN_RAW)!!
    private val vaultId: String = savedStateHandle.get<String>(CHAIN_COIN_PARAM_VAULT_ID)!!

    val uiState = MutableStateFlow(ChainTokensUiModel())

    fun loadData() {
        viewModelScope.launch {
            val chain = requireNotNull(Chain.entries.find { it.raw == chainRaw })
            accountsRepository.loadAddress(
                vaultId = vaultId,
                chain = chain,
            ).collect { address ->
                val totalFiatValue = address.accounts
                    .calculateAccountsTotalFiatValue()

                val tokens = address.accounts.map { account ->
                    ChainTokenUiModel(
                        name = account.token.ticker,
                        balance = account.tokenValue?.decimal?.toPlainString(),
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
                    ?.let(fiatValueToStringMapper::map) ?: ""

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
                    )
                }
            }
        }
    }

    fun send() {
        viewModelScope.launch {
            navigator.navigate(Destination.Send(vaultId, chainRaw))
        }
    }

    fun swap() {
        // TODO navigate to swap screen
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
}