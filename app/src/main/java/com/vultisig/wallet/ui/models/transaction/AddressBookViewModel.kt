package com.vultisig.wallet.ui.models.transaction

import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.intl.Locale
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.ImageModel
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.models.logo
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
) : ViewModel() {

    private val requestId: String? = savedStateHandle.get(Destination.ARG_REQUEST_ID)

    val state = MutableStateFlow(AddressBookUiModel())

    fun loadData() {
        viewModelScope.launch {
            val entries = addressBookRepository.getEntries()
            state.update { state ->
                state.copy(entries = entries.map {
                    AddressBookEntryUiModel(
                        model = it,
                        image = it.chain.logo,
                        name = it.title,
                        network = it.chain.name.capitalize(Locale.current),
                        address = it.address,
                    )
                })
            }
        }
    }

    fun selectAddress(model: AddressBookEntryUiModel) {
        if (requestId != null) {
            viewModelScope.launch {
                requestResultRepository.respond(requestId, model.model)
                navigator.navigate(Destination.Back)
            }
        }
    }

    fun deleteAddress(model: AddressBookEntryUiModel) {
        viewModelScope.launch {
            addressBookRepository.delete(model.model.chain, model.model.address)
            loadData()
        }
    }

    fun toggleEditMode() {
        state.update { it.copy(isEditModeEnabled = !it.isEditModeEnabled) }
    }

    fun addAddress() {
        viewModelScope.launch {
            navigator.navigate(Destination.AddAddressEntry)
        }
    }

}