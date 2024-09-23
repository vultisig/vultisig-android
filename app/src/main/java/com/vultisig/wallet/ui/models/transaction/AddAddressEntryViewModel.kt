@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.compose.foundation.text2.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.utils.UiText
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class AddAddressEntryUiModel(
    val selectedChain: Chain = Chain.Ethereum,
    val chains: List<Chain> = Chain.entries,
    val addressError: UiText? = null,
)

@HiltViewModel
internal class AddAddressEntryViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val addressBookRepository: AddressBookRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {

    val state = MutableStateFlow(AddAddressEntryUiModel())

    val titleTextFieldState = TextFieldState()
    val addressTextFieldState = TextFieldState()

    fun selectChain(chain: Chain) {
        state.update { it.copy(selectedChain = chain) }
    }

    fun saveAddress() {
        val chain = state.value.selectedChain
        val title = titleTextFieldState.text.toString()
        val address = addressTextFieldState.text.toString()

        if (validateAddress(chain, address) != null) {
            return
        }

        viewModelScope.launch {
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
            UiText.StringResource(R.string.send_error_no_address)
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