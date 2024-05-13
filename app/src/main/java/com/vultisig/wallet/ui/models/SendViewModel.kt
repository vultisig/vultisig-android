package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.Account
import com.vultisig.wallet.data.on_board.db.VaultDB
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.ui.models.mappers.AccountToTokenBalanceUiModelMapper
import com.vultisig.wallet.ui.navigation.Destination.Send.Companion.ARG_CHAIN_ID
import com.vultisig.wallet.ui.navigation.Destination.Send.Companion.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
internal data class TokenBalanceUiModel(
    val model: Account,
    val title: String,
    val balance: String?,
    @DrawableRes val logo: Int,
)

@Immutable
internal data class SendUiModel(
    val selectedCoin: TokenBalanceUiModel? = null,
    val availableTokens: List<TokenBalanceUiModel> = emptyList(),
    val isTokensExpanded: Boolean = false,
    val from: String = "",
    val to: String = "",
    val tokenAmount: String = "",
    val fiatAmount: String = "",
    val fee: String? = null,
)

@HiltViewModel
internal class SendViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultDb: VaultDB,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val accountsRepository: AccountsRepository,
    private val accountToTokenBalanceUiModelMapper: AccountToTokenBalanceUiModelMapper,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[ARG_VAULT_ID])
    private val chainRaw: String =
        requireNotNull(savedStateHandle[ARG_CHAIN_ID])

    private val selectedAccount = MutableStateFlow<Account?>(null)

    val uiState = MutableStateFlow(SendUiModel())

    init {
        loadTokens()
        collectSelectedAccount()
    }

    fun selectToken(token: TokenBalanceUiModel) {
        selectedAccount.value = token.model
        toggleTokens()
    }

    fun toggleTokens() {
        uiState.update {
            it.copy(isTokensExpanded = !it.isTokensExpanded)
        }
    }

    private fun loadTokens() {
        viewModelScope.launch {
            val vault = requireNotNull(vaultDb.select(vaultId))

            val chain = requireNotNull(Chain.entries.find { it.raw == chainRaw })

            val address = chainAccountAddressRepository.getAddress(chain, vault)

            accountsRepository.loadChainAccounts(
                vaultId = vaultId,
                chain = chain,
            ).collect { accounts ->
                val tokenUiModels = accounts
                    .map(accountToTokenBalanceUiModelMapper::map)

                val accountOfNativeToken = accounts.find { it.token.isNativeToken }
                val selectedAccountValue = selectedAccount.value
                // so it doesnt reset user selection of token on update
                if (selectedAccountValue == null ||
                    selectedAccountValue.token.ticker == accountOfNativeToken?.token?.ticker
                ) {
                    selectedAccount.value = accountOfNativeToken
                }

                uiState.update {
                    it.copy(
                        from = address,
                        availableTokens = tokenUiModels,
                    )
                }
            }
        }
    }

    private fun collectSelectedAccount() {
        viewModelScope.launch {
            selectedAccount.collect { selectedAccount ->
                val uiModel = selectedAccount
                    ?.let(accountToTokenBalanceUiModelMapper::map)
                uiState.update {
                    it.copy(selectedCoin = uiModel)
                }
            }
        }
    }

}