package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.calculateTotalFiatValue
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.models.Coins
import com.vultisig.wallet.models.IsDepositSupported
import com.vultisig.wallet.models.IsSwapSupported
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
data class ChainCoinUiModel(
    val chainName: String = "",
    val chainAddress: String = "",
    val totalBalance: String = "",
    val explorerURL: String = "",
    val tokens: List<ChainTokenUiModel> = emptyList(),
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
internal class ChainCoinViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultDB: VaultDB,
    private val addressRepository: ChainAccountAddressRepository,
    private val accountsRepository: AccountsRepository,
    private val fiatValueToStringMapper: FiatValueToStringMapper,
    private val navigator: Navigator<Destination>,
) : ViewModel() {
    private val chainRaw: String = savedStateHandle.get<String>(CHAIN_COIN_PARAM_CHAIN_RAW)!!
    private val vaultId: String = savedStateHandle.get<String>(CHAIN_COIN_PARAM_VAULT_ID)!!

    val uiState = MutableStateFlow(ChainCoinUiModel())
    val canSwap = MutableStateFlow(true)
    val canDeposit = MutableStateFlow(true)
    fun loadData() {
        viewModelScope.launch {
            val vault = requireNotNull(vaultDB.select(vaultId))

            val chain = requireNotNull(Chain.entries.find { it.raw == chainRaw })
            canSwap.emit(chain.IsSwapSupported)
            canDeposit.emit(chain.IsDepositSupported)
            accountsRepository.loadChainAccounts(
                vaultId = vaultId,
                chain = chain,
            ).collect { accounts ->
                val totalFiatValue = accounts.calculateTotalFiatValue()

                val tokens = accounts.map { account ->
                    ChainTokenUiModel(
                        name = account.token.ticker,
                        balance = account.tokenValue?.decimal?.toPlainString(),
                        fiatBalance = account.fiatValue
                            ?.let(fiatValueToStringMapper::map),
                        tokenLogo = Coins.getCoinLogo(account.token.logo),
                        chainLogo = chain.logo,
                    )
                }

                uiState.update {
                    it.copy(
                        chainName = chainRaw,
                        chainAddress = accounts.firstOrNull()?.address ?: "",
                        tokens = tokens,
                        explorerURL = accounts.firstOrNull()?.blockExplorerUrl ?: "",
                        totalBalance = totalFiatValue
                            ?.let(fiatValueToStringMapper::map) ?: ""
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