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
    val resolvedDstAddress: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    val dstAddressLabel: StateFlow<String?>
        field = MutableStateFlow<String?>(null)

    // True while the destination tag was auto-filled from a pasted X-address (locks the field).
    val destinationTagLocked: StateFlow<Boolean>
        field = MutableStateFlow(false)

    // The classic address an X-address normalized to; used to keep the lock while the field holds
    // that normalized value, and to release it once the user replaces the address.
    private var lockedClassicAddress: String? = null

    val isDstAddressComplete: StateFlow<Boolean>
        field = MutableStateFlow(false)

    // Why a non-empty recipient was rejected, or null once the input is accepted or empty. Drives
    // the inline recipient error. While name resolution is in flight the value is held, so a
    // standing error stays put until the new input resolves instead of blinking off and back on.
    // Chain-general: every send chain validates through the same path below.
    val addressError: StateFlow<RecipientValidity?>
        field = MutableStateFlow<RecipientValidity?>(null)

    val onAddressValidated: SharedFlow<Unit>
        field = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

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
            isDstAddressComplete.value = text.toString().isNotBlank()
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
            resolvedDstAddress.value = null
            dstAddressLabel.value = null
            addressError.value = null
            return
        }

        when (val validity = chainAccountAddressRepository.validateRecipient(chain, addressStr)) {
            RecipientValidity.Valid -> {
                // Only clear ENS label if the user typed a new raw address,
                // not when we programmatically set the field to the resolved address.
                if (addressStr != resolvedDstAddress.value) {
                    dstAddressLabel.value = null
                }
                resolvedDstAddress.value = addressStr
                addressError.value = null
                onAddressValidated.tryEmit(Unit)
            }
            // A token account, program address or burn address is a well-formed address, so
            // there is no name for the resolver to find — reject it here instead of sending it
            // round that path.
            RecipientValidity.NotAWalletAddress,
            RecipientValidity.BurnAddress -> {
                resolvedDstAddress.value = null
                dstAddressLabel.value = null
                addressError.value = validity
            }
            RecipientValidity.InvalidForChain -> {
                // Clear stale resolved address while async resolution is in-flight
                resolvedDstAddress.value = null
                dstAddressLabel.value = null
                tryResolveName(addressStr, token)
            }
        }
    }

    /**
     * Releases an X-address-derived destination-tag lock. Only a tag the X-address derived (which
     * was locked) is dropped; a hand-typed tag is user intent and is preserved.
     */
    private fun releaseDerivedTagLock() {
        if (destinationTagLocked.value) destinationTagFieldState.clearText()
        destinationTagLocked.value = false
        lockedClassicAddress = null
    }

    private fun applyXAddress(decoded: RippleDestinationTag.XAddress, originalInput: String) {
        val tag = decoded.tag
        if (tag != null) {
            destinationTagFieldState.setTextAndPlaceCursorAtEnd(tag.toString())
            destinationTagLocked.value = true
        } else {
            // No embedded tag: normalize the address and leave the tag field editable, but drop a
            // tag a *previous* X-address derived (it was locked) so it can't ride onto this new
            // address; a hand-typed tag is user intent and is preserved.
            if (destinationTagLocked.value) destinationTagFieldState.clearText()
            destinationTagLocked.value = false
        }
        lockedClassicAddress = decoded.classicAddress
        if (addressFieldState.text.toString() != decoded.classicAddress) {
            addressFieldState.setTextAndPlaceCursorAtEnd(decoded.classicAddress)
        }
        resolvedDstAddress.value = decoded.classicAddress
        addressError.value = null
        // Surface the pasted X-address as the label so Verify/Done show what the user entered.
        dstAddressLabel.value = originalInput
        onAddressValidated.tryEmit(Unit)
    }

    private suspend fun tryResolveName(addressStr: String, token: Coin) {
        val chain = token.chain
        try {
            val resolved = addressParserRepository.resolveName(addressStr, chain)
            // Ignore stale result if user changed input while resolving
            if (addressFieldState.text.asAddressInput() != addressStr) return
            val validity = chainAccountAddressRepository.validateRecipient(chain, resolved)
            if (validity == RecipientValidity.Valid) {
                dstAddressLabel.value = addressStr
                resolvedDstAddress.value = resolved
                addressError.value = null
                addressFieldState.setTextAndPlaceCursorAtEnd(resolved)
                onAddressValidated.tryEmit(Unit)
            } else {
                resolvedDstAddress.value = null
                dstAddressLabel.value = null
                addressError.value = validity
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Resolver failures are non-fatal (the user can retry), but log at warning so a
            // genuine bug in the resolver surface — RPC, parsing, etc. — isn't silently buried.
            Timber.w(e, "Failed to resolve address %s on %s", addressStr, chain)
            resolvedDstAddress.value = null
            dstAddressLabel.value = null
            addressError.value = RecipientValidity.InvalidForChain
        }
    }
}
