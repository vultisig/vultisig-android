package com.vultisig.wallet.ui.screens.transaction

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.AddressBookEntry
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.logo
import com.vultisig.wallet.data.repositories.AddressBookRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RequestResultRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddressBookBottomSheetUiModel(
    val addresses: List<AddressEntryUiModel> = emptyList(),
    val vaults: List<AddressEntryUiModel> = emptyList(),
)

data class AddressEntryUiModel(
    val model: AddressBookEntry,
    val title: String,
    val subtitle: String,
    val image: Int? = null,
)

@HiltViewModel
internal class AddressBookBottomSheetViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val addressBookRepository: AddressBookRepository,
    private val vaultRepository: VaultRepository,
    private val requestResultRepository: RequestResultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<Route.AddressBook>()

    val state = MutableStateFlow(AddressBookBottomSheetUiModel())

    init {
        viewModelScope.launch {
            val entries = addressBookRepository
                .getEntries()
                .filter {
                    chainAccountAddressRepository
                        .isValid(
                            chain = Chain.fromRaw(args.chainId),
                            address = it.address
                        )
                }
                .map {
                    AddressEntryUiModel(
                        model = it,
                        title = it.title,
                        subtitle = it.address,
                        image = it.chain.logo,
                    )
                }

            state.update {
                it.copy(
                    addresses = entries,
                )
            }
        }

        viewModelScope.launch {
            val vaults = vaultRepository.getAll()
                .mapNotNull { vault ->
                    vault.coins
                        .find { it.chain.id == args.chainId }
                        ?.address
                        ?.let { existingAddress ->
                            AddressEntryUiModel(
                                model = AddressBookEntry(
                                    chain = Chain.fromRaw(args.chainId),
                                    address = existingAddress,
                                    title = vault.name,
                                ),
                                title = vault.name,
                                subtitle = existingAddress,
                            )
                        }
                }

            state.update {
                it.copy(
                    vaults = vaults
                )
            }
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }

    fun selectAddress(entry: AddressEntryUiModel) {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, entry.model)
            navigator.back()
        }
    }

}