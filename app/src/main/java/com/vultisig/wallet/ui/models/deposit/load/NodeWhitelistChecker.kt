package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.TextFieldState
import com.vultisig.wallet.R
import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.MayachainBondRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.models.deposit.DepositFormUiModel
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.utils.UiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * Owns the MAYAChain Bond node-whitelist check extracted from `DepositFormViewModel`: looks up the
 * selected node's bond providers and verifies the user's address is whitelisted, writing the
 * resulting `nodeAddressError` / `isCheckingWhitelist` / `isWhitelistFailed` flags back into the
 * shared [state].
 *
 * The repository is Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and supplies
 * it (assisted) along with the shared UI [state], the [address] flow, the form-owned
 * [nodeAddressFieldState] and the [chainProvider] accessor so this checker never owns its own scope
 * or VM state.
 */
internal class NodeWhitelistChecker
@AssistedInject
constructor(
    private val mayachainBondRepository: MayachainBondRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val state: MutableStateFlow<DepositFormUiModel>,
    @Assisted private val address: StateFlow<Address?>,
    @Assisted private val nodeAddressFieldState: TextFieldState,
    @Assisted private val chainProvider: () -> Chain?,
) {

    /** @see NodeWhitelistChecker */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [NodeWhitelistChecker] bound to the given scope, shared state, address flow,
         * node-address field and chain accessor.
         */
        fun create(
            scope: CoroutineScope,
            state: MutableStateFlow<DepositFormUiModel>,
            address: StateFlow<Address?>,
            nodeAddressFieldState: TextFieldState,
            chainProvider: () -> Chain?,
        ): NodeWhitelistChecker
    }

    private var whitelistJob: Job? = null

    /**
     * Starts a whitelist check for [nodeAddress], cancelling any in-flight check first and marking
     * the form as checking before the network lookup runs.
     */
    fun check(nodeAddress: String) {
        whitelistJob?.cancel()
        state.update {
            it.copy(nodeAddressError = null, isCheckingWhitelist = true, isWhitelistFailed = false)
        }
        whitelistJob = scope.safeLaunch { checkNodeWhitelist(nodeAddress) }
    }

    /** Cancels any in-flight whitelist check. */
    fun cancel() {
        whitelistJob?.cancel()
    }

    private suspend fun checkNodeWhitelist(nodeAddress: String) {
        try {
            val userAddress =
                withTimeoutOrNull(ADDRESS_AWAIT_TIMEOUT_MS) { address.filterNotNull().first() }
                    ?.address
                    ?: run {
                        state.update { it.copy(isCheckingWhitelist = false) }
                        return
                    }
            val nodeInfo = mayachainBondRepository.getNodeDetails(nodeAddress)
            if (
                nodeAddressFieldState.text.toString() != nodeAddress ||
                    chainProvider() != Chain.MayaChain ||
                    state.value.depositOption != DepositOption.Bond
            ) {
                state.update { it.copy(isCheckingWhitelist = false) }
                return
            }
            val isWhitelisted =
                nodeInfo.bondProviders.providers.any { it.bondAddress == userAddress }
            if (!isWhitelisted) {
                state.update {
                    it.copy(
                        nodeAddressError =
                            UiText.StringResource(R.string.bond_not_whitelisted_error),
                        isCheckingWhitelist = false,
                        isWhitelistFailed = true,
                    )
                }
            } else {
                state.update {
                    it.copy(
                        nodeAddressError = null,
                        isCheckingWhitelist = false,
                        isWhitelistFailed = false,
                    )
                }
            }
        } catch (ce: CancellationException) {
            throw ce
        } catch (e: Exception) {
            Timber.w(e, "Whitelist check failed for node %s", nodeAddress)
            state.update {
                it.copy(
                    nodeAddressError = UiText.StringResource(R.string.dialog_default_error_body),
                    isCheckingWhitelist = false,
                    isWhitelistFailed = true,
                )
            }
        }
    }

    private companion object {
        private const val ADDRESS_AWAIT_TIMEOUT_MS = 5_000L
    }
}
