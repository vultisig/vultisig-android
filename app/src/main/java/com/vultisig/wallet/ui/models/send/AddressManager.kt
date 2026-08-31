package com.vultisig.wallet.ui.models.send

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.chains.helpers.RippleDestinationTag
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.AddressParserRepository
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.RecipientValidity
import com.vultisig.wallet.data.usecases.RequestAddressBookEntryUseCase
import com.vultisig.wallet.data.utils.safeLaunch
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
    // XRP destination-tag field; X-address decoding autofills and locks it.
    private val destinationTagFieldState: TextFieldState,
    private val selectedToken: StateFlow<Coin?>,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val addressParserRepository: AddressParserRepository,
    private val requestAddressBookEntry: RequestAddressBookEntryUseCase,
    private val vaultIdProvider: () -> String?,
    private val checkIfTokenSelectionRequired: (currentChain: Chain, newChain: Chain) -> Unit,
) {
    private val _resolvedDstAddress = MutableStateFlow<String?>(null)
    val resolvedDstAddress: StateFlow<String?> = _resolvedDstAddress.asStateFlow()

    private val _dstAddressLabel = MutableStateFlow<String?>(null)
    val dstAddressLabel: StateFlow<String?> = _dstAddressLabel.asStateFlow()

    // True while the destination tag was auto-filled from a pasted X-address (locks the field).
    private val _destinationTagLocked = MutableStateFlow(false)
    val destinationTagLocked: StateFlow<Boolean> = _destinationTagLocked.asStateFlow()

    // The classic address an X-address normalized to; used to keep the lock while the field holds
    // that normalized value, and to release it once the user replaces the address.
    private var lockedClassicAddress: String? = null

    private val _isDstAddressComplete = MutableStateFlow(false)
    val isDstAddressComplete: StateFlow<Boolean> = _isDstAddressComplete.asStateFlow()

    // Why a non-empty recipient was rejected, or null once the input is accepted or empty. Drives
    // the inline recipient error. While name resolution is in flight the value is held, so a
    // standing error stays put until the new input resolves instead of blinking off and back on.
    // Chain-general: every send chain validates through the same path below.
    private val _addressError = MutableStateFlow<RecipientValidity?>(null)
    val addressError: StateFlow<RecipientValidity?> = _addressError.asStateFlow()

    private val _onAddressValidated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val onAddressValidated: SharedFlow<Unit> = _onAddressValidated.asSharedFlow()

    fun start() {
        scope.launch { collectIsComplete() }
        scope.launch { collectResolvedAddress() }
    }

    fun setOutputAddress(address: String) {
        addressFieldState.setTextAndPlaceCursorAtEnd(address)
    }

    /** Opens the address book and applies the chosen entry to the output address field. */
    fun openAddressBook() {
        scope.safeLaunch {
            val vaultId = vaultIdProvider() ?: return@safeLaunch
            val selectedChain = selectedToken.value?.chain ?: return@safeLaunch

            val address =
                requestAddressBookEntry(chainId = selectedChain.id, excludeVaultId = vaultId)
                    ?: return@safeLaunch

            checkIfTokenSelectionRequired(selectedChain, address.chain)
            setOutputAddress(address.address)
        }
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

        if (chain == Chain.Ripple) {
            if (addressStr.startsWith("X")) {
                val decoded = RippleDestinationTag.decodeXAddress(addressStr)
                if (decoded != null) {
                    applyXAddress(decoded, originalInput = addressStr)
                    return
                }
                // Invalid X-address: drop any stale lock from a previous valid X-address before
                // falling through to the normal invalid-address handling below.
                if (lockedClassicAddress != null) releaseDerivedTagLock()
            } else if (lockedClassicAddress != null && addressStr != lockedClassicAddress) {
                // The user replaced the normalized address with a different one.
                releaseDerivedTagLock()
            }
        }

        if (addressStr.isEmpty()) {
            _resolvedDstAddress.value = null
            _dstAddressLabel.value = null
            _addressError.value = null
            return
        }

        when (chainAccountAddressRepository.validateRecipient(chain, addressStr)) {
            RecipientValidity.Valid -> {
                // Only clear ENS label if the user typed a new raw address,
                // not when we programmatically set the field to the resolved address.
                if (addressStr != _resolvedDstAddress.value) {
                    _dstAddressLabel.value = null
                }
                _resolvedDstAddress.value = addressStr
                _addressError.value = null
                _onAddressValidated.tryEmit(Unit)
            }
            // A token account or program address is a well-formed address, so there is no name for
            // the resolver to find — reject it here instead of sending it round that path.
            RecipientValidity.NotAWalletAddress -> {
                _resolvedDstAddress.value = null
                _dstAddressLabel.value = null
                _addressError.value = RecipientValidity.NotAWalletAddress
            }
            RecipientValidity.InvalidForChain -> {
                // Clear stale resolved address while async resolution is in-flight
                _resolvedDstAddress.value = null
                _dstAddressLabel.value = null
                tryResolveName(addressStr, token)
            }
        }
    }

    /**
     * Releases an X-address-derived destination-tag lock. Only a tag the X-address derived (which
     * was locked) is dropped; a hand-typed tag is user intent and is preserved.
     */
    private fun releaseDerivedTagLock() {
        if (_destinationTagLocked.value) destinationTagFieldState.clearText()
        _destinationTagLocked.value = false
        lockedClassicAddress = null
    }

    private fun applyXAddress(decoded: RippleDestinationTag.XAddress, originalInput: String) {
        val tag = decoded.tag
        if (tag != null) {
            destinationTagFieldState.setTextAndPlaceCursorAtEnd(tag.toString())
            _destinationTagLocked.value = true
        } else {
            // No embedded tag: normalize the address and leave the tag field editable, but drop a
            // tag a *previous* X-address derived (it was locked) so it can't ride onto this new
            // address; a hand-typed tag is user intent and is preserved.
            if (_destinationTagLocked.value) destinationTagFieldState.clearText()
            _destinationTagLocked.value = false
        }
        lockedClassicAddress = decoded.classicAddress
        if (addressFieldState.text.toString() != decoded.classicAddress) {
            addressFieldState.setTextAndPlaceCursorAtEnd(decoded.classicAddress)
        }
        _resolvedDstAddress.value = decoded.classicAddress
        _addressError.value = null
        // Surface the pasted X-address as the label so Verify/Done show what the user entered.
        _dstAddressLabel.value = originalInput
        _onAddressValidated.tryEmit(Unit)
    }

    private suspend fun tryResolveName(addressStr: String, token: Coin) {
        val chain = token.chain
        try {
            val resolved = addressParserRepository.resolveName(addressStr, chain)
            // Ignore stale result if user changed input while resolving
            if (addressFieldState.text.asAddressInput() != addressStr) return
            val validity = chainAccountAddressRepository.validateRecipient(chain, resolved)
            if (validity == RecipientValidity.Valid) {
                _dstAddressLabel.value = addressStr
                _resolvedDstAddress.value = resolved
                _addressError.value = null
                addressFieldState.setTextAndPlaceCursorAtEnd(resolved)
                _onAddressValidated.tryEmit(Unit)
            } else {
                _resolvedDstAddress.value = null
                _dstAddressLabel.value = null
                _addressError.value = validity
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Resolver failures are non-fatal (the user can retry), but log at warning so a
            // genuine bug in the resolver surface — RPC, parsing, etc. — isn't silently buried.
            Timber.w(e, "Failed to resolve address %s on %s", addressStr, chain)
            _resolvedDstAddress.value = null
            _dstAddressLabel.value = null
            _addressError.value = RecipientValidity.InvalidForChain
        }
    }
}
