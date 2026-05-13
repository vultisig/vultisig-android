package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.ui.utils.asAddressInput
import com.vultisig.wallet.ui.utils.textAsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.launch
import timber.log.Timber

internal class AddressManager(
    private val scope: CoroutineScope,
    private val addressFieldState: TextFieldState,
    private val selectedToken: StateFlow<Coin?>,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val addressParserRepository: AddressParserRepository,
) {
    private val _resolvedDstAddress = MutableStateFlow<String?>(null)
    val resolvedDstAddress: StateFlow<String?> = _resolvedDstAddress.asStateFlow()

    private val _dstAddressLabel = MutableStateFlow<String?>(null)
    val dstAddressLabel: StateFlow<String?> = _dstAddressLabel.asStateFlow()

    private val _isDstAddressComplete = MutableStateFlow(false)
    val isDstAddressComplete: StateFlow<Boolean> = _isDstAddressComplete.asStateFlow()

    private val _onAddressValidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onAddressValidated: SharedFlow<Unit> = _onAddressValidated.asSharedFlow()

    fun start() {
        scope.launch { collectIsComplete() }
        scope.launch { collectResolvedAddress() }
    }

    fun setOutputAddress(address: String) {
        addressFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    private suspend fun collectIsComplete() {
        addressFieldState.textAsFlow().collect { text ->
            _isDstAddressComplete.value = text.toString().isNotBlank()
        }
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private suspend fun collectResolvedAddress() {
        addressFieldState
            .textAsFlow()
            .debounce(300)
            .combine(selectedToken.filterNotNull()) { address, token ->
                address.asAddressInput() to token
            }
            .mapLatest { (addressStr, token) -> handleAddressInput(addressStr, token) }
            .collect()
    }

    private suspend fun handleAddressInput(addressStr: String, token: Coin) {
        val chain = token.chain
        when {
            chainAccountAddressRepository.isValid(chain, addressStr) -> {
                // Only clear ENS label if the user typed a new raw address,
                // not when we programmatically set the field to the resolved address.
                if (addressStr != _resolvedDstAddress.value) {
                    _dstAddressLabel.value = null
                }
                _resolvedDstAddress.value = addressStr
                _onAddressValidated.tryEmit(Unit)
            }
            addressStr.isNotEmpty() -> {
                // Clear stale resolved address while async resolution is in-flight
                _resolvedDstAddress.value = null
                _dstAddressLabel.value = null
                tryResolveName(addressStr, token)
            }
            else -> {
                _resolvedDstAddress.value = null
                _dstAddressLabel.value = null
            }
        }
    }

    private suspend fun tryResolveName(addressStr: String, token: Coin) {
        val chain = token.chain
        try {
            val resolved = addressParserRepository.resolveName(addressStr, chain)
            // Ignore stale result if user changed input while resolving
            if (addressFieldState.text.asAddressInput() != addressStr) return
            if (chainAccountAddressRepository.isValid(chain, resolved)) {
                _dstAddressLabel.value = addressStr
                _resolvedDstAddress.value = resolved
                addressFieldState.setTextAndPlaceCursorAtEnd(resolved)
                _onAddressValidated.tryEmit(Unit)
            } else {
                _resolvedDstAddress.value = null
                _dstAddressLabel.value = null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // Resolver failures are non-fatal (the user can retry), but log at warning so a
            // genuine bug in the resolver surface — RPC, parsing, etc. — isn't silently buried.
            Timber.w(e, "Failed to resolve address %s on %s", addressStr, chain)
            _resolvedDstAddress.value = null
            _dstAddressLabel.value = null
        }
    }
}
