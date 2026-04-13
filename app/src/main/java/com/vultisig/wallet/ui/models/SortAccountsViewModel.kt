package com.vultisig.wallet.ui.models

import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.db.models.AccountOrderEntity
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AccountOrderRepository
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.launch
import timber.log.Timber

@Immutable
internal data class SortAccountItemUiModel(
    val chainId: String,
    val chainName: String,
    @param:DrawableRes val logo: Int,
    val isPinned: Boolean,
)

@Immutable
internal data class SortAccountsUiModel(
    val pinnedAccounts: List<SortAccountItemUiModel> = emptyList(),
    val unpinnedAccounts: List<SortAccountItemUiModel> = emptyList(),
)

@HiltViewModel
internal class SortAccountsViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val accountsRepository: AccountsRepository,
    private val accountOrderRepository: AccountOrderRepository,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.SortAccounts>().vaultId

    val uiState = MutableStateFlow(SortAccountsUiModel())

    private var isLoaded = false

    init {
        loadAccounts()
    }

    private fun loadAccounts() {
        viewModelScope.launch {
            val addresses =
                try {
                    accountsRepository.loadAddresses(vaultId, false).first()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.e(e, "Failed to load addresses for sorting")
                    return@launch
                }
            val savedOrders = accountOrderRepository.loadOrders(vaultId).first()

            val orderMap = savedOrders.associateBy { it.chain }

            val items =
                addresses.map { address ->
                    val saved = orderMap[address.chain.raw]
                    SortAccountItemUiModel(
                        chainId = address.chain.raw,
                        chainName = address.chain.raw,
                        logo = address.chain.logo,
                        isPinned = saved?.isPinned == true,
                    )
                }

            val pinned =
                items
                    .filter { it.isPinned }
                    .sortedBy { orderMap[it.chainId]?.order ?: Float.MAX_VALUE }
            val unpinned =
                items
                    .filter { !it.isPinned }
                    .sortedBy { orderMap[it.chainId]?.order ?: Float.MAX_VALUE }

            uiState.update { it.copy(pinnedAccounts = pinned, unpinnedAccounts = unpinned) }
            isLoaded = true
        }
    }

    fun movePinned(from: Int, to: Int) {
        uiState.update {
            if (from !in it.pinnedAccounts.indices || to !in it.pinnedAccounts.indices)
                return@update it
            val list = it.pinnedAccounts.toMutableList().apply { add(to, removeAt(from)) }
            it.copy(pinnedAccounts = list)
        }
    }

    fun moveUnpinned(from: Int, to: Int) {
        uiState.update {
            if (from !in it.unpinnedAccounts.indices || to !in it.unpinnedAccounts.indices)
                return@update it
            val list = it.unpinnedAccounts.toMutableList().apply { add(to, removeAt(from)) }
            it.copy(unpinnedAccounts = list)
        }
    }

    fun togglePin(item: SortAccountItemUiModel) {
        uiState.update { state ->
            if (item.isPinned) {
                val updated = item.copy(isPinned = false)
                state.copy(
                    pinnedAccounts = state.pinnedAccounts.filter { it.chainId != item.chainId },
                    unpinnedAccounts = listOf(updated) + state.unpinnedAccounts,
                )
            } else {
                val updated = item.copy(isPinned = true)
                state.copy(
                    pinnedAccounts = state.pinnedAccounts + updated,
                    unpinnedAccounts = state.unpinnedAccounts.filter { it.chainId != item.chainId },
                )
            }
        }
    }

    fun save() {
        viewModelScope.launch {
            if (!isLoaded) {
                Timber.w("Cannot save account order: data not loaded yet")
                return@launch
            }

            val state = uiState.value
            val orders = mutableListOf<AccountOrderEntity>()

            state.pinnedAccounts.forEachIndexed { index, item ->
                orders.add(
                    AccountOrderEntity(
                        vaultId = vaultId,
                        chain = item.chainId,
                        order = index.toFloat(),
                        isPinned = true,
                    )
                )
            }

            state.unpinnedAccounts.forEachIndexed { index, item ->
                orders.add(
                    AccountOrderEntity(
                        vaultId = vaultId,
                        chain = item.chainId,
                        order = (state.pinnedAccounts.size + index).toFloat(),
                        isPinned = false,
                    )
                )
            }

            try {
                accountOrderRepository.saveOrders(vaultId, orders)
                navigator.back()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.e(e, "Failed to save account order")
            }
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }
}
