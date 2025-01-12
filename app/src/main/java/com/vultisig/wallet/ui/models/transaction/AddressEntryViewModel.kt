package com.vultisig.wallet.ui.models.transaction

import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.order.OrderRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class AddAddressEntryUiModel(
    @StringRes val titleRes: Int = R.string.add_address_title,
    val selectedChain: Chain = Chain.Ethereum,
    val chains: List<Chain> = Chain.entries,
    val addressError: UiText? = null,
)

@HiltViewModel
internal class AddressEntryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val addressBookRepository: AddressBookRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val orderRepository: OrderRepository<AddressBookOrderEntity>,
) : ViewModel() {

    val state = MutableStateFlow(AddAddressEntryUiModel())

    private val addressBookEntryChainId = savedStateHandle.get<String?>(Destination.ARG_CHAIN_ID)

    private val addressBookEntryAddress = savedStateHandle.get<String?>(Destination.ARG_ADDRESS)

    val titleTextFieldState = TextFieldState()
    val addressTextFieldState = TextFieldState()

    init {
        viewModelScope.launch {
            if (addressBookEntryChainId != null && addressBookEntryAddress != null) {
                val addressBookEntry = addressBookRepository.getEntry(
                    chainId = addressBookEntryChainId,
                    address = addressBookEntryAddress
                )
                state.update {
                    it.copy(
                        titleRes = R.string.edit_address_title,
                        selectedChain = addressBookEntry.chain,
                    )
                }
                titleTextFieldState.setTextAndPlaceCursorAtEnd(addressBookEntry.title)
                addressTextFieldState.setTextAndPlaceCursorAtEnd(addressBookEntry.address)
            }
        }
    }

    fun selectChain(chain: Chain) {
        state.update { it.copy(selectedChain = chain) }
    }

    fun saveAddress() {
        val chain = state.value.selectedChain
        val title = titleTextFieldState.text.toString()
        val address = addressTextFieldState.text.toString()
        validateAddress(chain, address)?.let {
            state.update {
                it.copy(
                    addressError = UiText.FormattedText(
                        R.string.address_bookmark_error_invalid_address,
                        listOf(chain)
                    )
                )
            }
            return
        }

        viewModelScope.launch {
            if (addressBookEntryChainId != null && addressBookEntryAddress != null) {
                addressBookRepository.delete(addressBookEntryChainId, addressBookEntryAddress)
                val orderName = "${addressBookEntryChainId}-${addressBookEntryAddress}"
                val order = orderRepository.find(parentId = null, name = orderName)
                orderRepository.delete(null, orderName)
                order?.let { orderRepository.insert(it) }
            }
            addressBookRepository.add(
                AddressBookEntry(
                    chain = chain,
                    address = address,
                    title = title
                )
            )

            navigator.navigate(Destination.Back)
        }
    }

    fun validateAddress() {
        val address = addressTextFieldState.text.toString()
        val chain = state.value.selectedChain

        val error = validateAddress(chain, address)
        state.update { it.copy(addressError = error) }
    }

    private fun validateAddress(chain: Chain, address: String): UiText? =
        if (address.isBlank() || !chainAccountAddressRepository.isValid(chain, address)) {
            UiText.FormattedText(
                R.string.address_bookmark_error_invalid_address,
                listOf(chain)
            )
        } else {
            null
        }

    fun scanAddress() {
        viewModelScope.launch {
            navigator.navigate(Destination.ScanQr)
        }
    }

    fun setOutputAddress(address: String) {
        addressTextFieldState.setTextAndPlaceCursorAtEnd(address)
    }

}