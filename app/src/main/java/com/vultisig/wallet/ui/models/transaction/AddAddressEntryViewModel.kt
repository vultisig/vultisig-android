@file:OptIn(ExperimentalFoundationApi::class)

package com.vultisig.wallet.ui.models.transaction

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text2.input.TextFieldState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.models.Chain
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class AddAddressEntryUiModel(
    val selectedChain: Chain = Chain.ethereum,
    val chains: List<Chain> = Chain.entries,
)

@HiltViewModel
internal class AddAddressEntryViewModel @Inject constructor(
    private val navigator: Navigator<Destination>,
    private val addressBookRepository: AddressBookRepository,
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

}