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
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
internal class AddressBookBottomSheetViewModel
@Inject
constructor(
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
            val entries =
                addressBookRepository
                    .getEntries()
                    .filter {
                        chainAccountAddressRepository.isValid(
                            chain = Chain.fromRaw(args.chainId),
                            address = it.address,
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

            state.update { it.copy(addresses = entries) }
        }

        viewModelScope.launch {
            val chain = Chain.fromRaw(args.chainId)
            val vaults =
                vaultRepository.getAll().mapNotNull { vault ->
                    // Prefer the already-enabled coin's address; otherwise derive the address
                    // from the vault's public key so vaults that haven't enabled this chain are
                    // still selectable as a destination.
                    val address =
                        vault.coins.find { it.chain.id == args.chainId }?.address
                            ?: try {
                                chainAccountAddressRepository.getAddress(chain, vault).first
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                null
                            }

                    address?.let {
                        AddressEntryUiModel(
                            model =
                                AddressBookEntry(chain = chain, address = it, title = vault.name),
                            title = vault.name,
                            subtitle = it,
                        )
                    }
                }

            state.update { it.copy(vaults = vaults) }
        }
    }

    fun back() {
        viewModelScope.launch { navigator.back() }
    }

    fun selectAddress(entry: AddressEntryUiModel) {
        viewModelScope.launch {
            requestResultRepository.respond(args.requestId, entry.model)
            navigator.back()
        }
    }
}
