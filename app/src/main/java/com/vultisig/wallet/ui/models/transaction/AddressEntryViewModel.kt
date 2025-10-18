package com.vultisig.wallet.ui.models.transaction

import androidx.annotation.StringRes
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.db.models.AddressBookOrderEntity
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.VaultId
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.order.OrderRepository
import com.vultisig.wallet.data.usecases.RequestQrScanUseCase
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.textAsFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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
    private val requestQrScan: RequestQrScanUseCase,
    private val addressBookRepository: AddressBookRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val orderRepository: OrderRepository<AddressBookOrderEntity>,
    private val requestResultRepository: RequestResultRepository,
) : ViewModel() {

    val state = MutableStateFlow(AddAddressEntryUiModel())

    private val addressBookEntryChainId = savedStateHandle.toRoute<Route.AddressEntry>().chainId

    private val addressBookEntryAddress = savedStateHandle.toRoute<Route.AddressEntry>().address

    private val vaultId = savedStateHandle.toRoute<Route.AddressEntry>().vaultId

    private var addressExist : Boolean = false

    val titleTextFieldState = TextFieldState()
    val addressTextFieldState = TextFieldState()

    init {
        viewModelScope.launch {
            if (!addressBookEntryChainId.isNullOrBlank() && !addressBookEntryAddress.isNullOrBlank()) {
                addressExist = addressBookRepository.entryExists(
                    addressBookEntryChainId,
                    addressBookEntryAddress
                )

                if (addressExist) {
                    editAddress(
                        addressBookEntryChainId = addressBookEntryChainId,
                        addressBookEntryAddress = addressBookEntryAddress
                    )
                } else {
                    createAddress(
                        addressBookEntryChainId = addressBookEntryChainId,
                        addressBookEntryAddress = addressBookEntryAddress
                    )
                }
            }
        }


        combine(
            state.map { it.selectedChain }.distinctUntilChanged(),
            addressTextFieldState.textAsFlow().filter { it.isNotEmpty() },
        ) { chain, address ->
            val error = validateAddress(
                chain = chain,
                address = address.toString()
            )
            state.update {
                it.copy(
                    addressError = error
                )
            }
        }
            .launchIn(viewModelScope)

    }

    private fun createAddress(
        addressBookEntryChainId: String,
        addressBookEntryAddress: String,
    ) {
        state.update {
            it.copy(
                titleRes = R.string.add_address_title,
                selectedChain = Chain.fromRaw(addressBookEntryChainId),
            )
        }
        addressTextFieldState.setTextAndPlaceCursorAtEnd(addressBookEntryAddress)
    }

    private suspend fun editAddress(
        addressBookEntryChainId: String,
        addressBookEntryAddress: String,
    ) {
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


    @OptIn(ExperimentalUuidApi::class)
    private suspend fun selectNetwork(
        vaultId: VaultId,
        selectedChain: Chain,
    ): Chain? {
        val requestId = Uuid.random().toString()
        navigator.route(
            Route.SelectNetwork(
                vaultId = vaultId,
                selectedNetworkId = selectedChain.id,
                requestId = requestId,
                filters = Route.SelectNetwork.Filters.None,
            )
        )

        val chain: Chain = requestResultRepository.request(requestId)
            ?: return null

        if (chain == selectedChain) {
            return null
        }

        return chain
    }

    fun selectChain(chain: Chain) {
        viewModelScope.launch {
            val selectedChain = selectNetwork(
                vaultId = vaultId,
                selectedChain = chain,
            ) ?: return@launch

            state.update {
                it.copy(selectedChain = selectedChain)
            }
        }
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
            if (!addressBookEntryChainId.isNullOrBlank() && !addressBookEntryAddress.isNullOrBlank() && addressExist) {
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
            if (!addressBookEntryChainId.isNullOrBlank() && !addressBookEntryAddress.isNullOrBlank() && addressExist.not()) {
                navigator.navigate(
                    dst = Destination.Home(),
                    opts = NavigationOptions(
                        clearBackStack = true
                    )
                )
            } else {
                navigator.navigate(Destination.Back)
            }
        }
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
            val qr = requestQrScan()
            if (!qr.isNullOrBlank()) {
                setOutputAddress(qr)
            }
        }
    }

    fun setOutputAddress(address: String) {
        addressTextFieldState.setTextAndPlaceCursorAtEnd(address)
    }

}