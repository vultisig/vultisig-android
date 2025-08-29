package com.vultisig.wallet.ui.models.transaction

import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.order.OrderRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class AddressBookUiModel(
    val isEditModeEnabled: Boolean = false,
    val entries: List<AddressBookEntryUiModel> = emptyList(),
)

internal data class AddressBookEntryUiModel(
    val model: AddressBookEntry,
    val image: ImageModel,
    val name: String,
    val network: String,
    val address: String,
)

@HiltViewModel
internal class AddressBookViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val addressBookRepository: AddressBookRepository,
    private val requestResultRepository: RequestResultRepository,
    private val orderRepository: OrderRepository<AddressBookOrderEntity>,
) : ViewModel() {

    private val requestId: String? = savedStateHandle[Destination.ARG_REQUEST_ID]
    private val chainId: String? = savedStateHandle[Destination.ARG_CHAIN_ID]
    private val vaultId: String = requireNotNull(savedStateHandle[Destination.ARG_VAULT_ID])

    private var reIndexJob: Job? = null

    private val addressBookEntries = MutableStateFlow<List<AddressBookEntry>>(emptyList())

    val state = MutableStateFlow(AddressBookUiModel())

    init {
        collectEntries()
    }

    fun loadData() {
        val chain = chainId?.let(Chain::fromRaw)
        viewModelScope.launch {
            orderRepository.loadOrders(null).map { orders ->
                val orderMap = orders.associateBy { it.value }

                val entries = addressBookRepository.getEntries()
                    .let { entries ->
                        if (chain != null) {
                            entries.filter { it.chain == chain }
                        } else {
                            entries
                        }
                    }

                entries.forEach {
                    if (it.id !in orderMap) {
                        orderRepository.insert(null, it.id)
                    }
                }

                entries.sortedByDescending {
                    orderMap[it.id]?.order
                }
            }.collect(addressBookEntries)
        }
    }

    private fun collectEntries() {
        viewModelScope.launch {
            addressBookEntries.map { entries ->
                entries.map {
                    AddressBookEntryUiModel(
                        model = it,
                        image = it.chain.logo,
                        name = it.title,
                        network = it.chain.name.capitalize(Locale.current),
                        address = it.address,
                    )
                }
            }.collect { entries ->
                state.update { state ->
                    state.copy(entries = entries)
                }
            }
        }
    }

    fun clickAddress(model: AddressBookEntryUiModel) = viewModelScope.launch {
        if (state.value.isEditModeEnabled) {
            editAddress(model.model)
        } else {
            selectAddress(model)
        }
    }

    private suspend fun selectAddress(model: AddressBookEntryUiModel) {
        if (requestId != null) {
            requestResultRepository.respond(requestId, model.model)
            navigator.navigate(Destination.Back)
        }
    }

    private suspend fun editAddress(model: AddressBookEntry) {
        navigator.navigate(Destination.AddressEntry(model.chain.id, model.address, vaultId = vaultId))
    }

    fun deleteAddress(model: AddressBookEntryUiModel) {
        viewModelScope.launch {
            addressBookRepository.delete(model.model.chain.id, model.model.address)
            orderRepository.delete(null, model.model.id)
            loadData()
        }
    }

    fun toggleEditMode() {
        state.update { it.copy(isEditModeEnabled = !it.isEditModeEnabled) }
    }

    fun addAddress() {
        viewModelScope.launch {
            navigator.navigate(Destination.AddressEntry(vaultId = vaultId))
        }
    }

    fun move(from: Int, to: Int) {
        val updatedPositionsList = addressBookEntries.value.toMutableList().apply {
            add(to, removeAt(from))
        }
        addressBookEntries.value = updatedPositionsList

        reIndexJob?.cancel()
        reIndexJob = viewModelScope.launch {
            delay(500)
            val midOrder = updatedPositionsList[to].id
            val upperOrder = updatedPositionsList.getOrNull(to + 1)?.id
            val lowerOrder = updatedPositionsList.getOrNull(to - 1)?.id
            orderRepository.updateItemOrder(null, upperOrder, midOrder, lowerOrder)
        }
    }

}