package com.vultisig.wallet.ui.models.deposit.load

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.crypto.getChainName
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.models.Coin
import com.vultisig.wallet.data.repositories.ChainAccountAddressRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import timber.log.Timber

/**
 * Outcome of resolving a THORChain inbound vault address for a deposit. Promoted to a top-level
 * type so both [SecuredAssetLoader] and the (staying) Switch / secured-asset call sites in
 * `DepositFormViewModel` can share it.
 */
internal sealed class InboundAddressResult {
    /** The inbound vault [address] is available for deposits. */
    data class Available(val address: String) : InboundAddressResult()

    /** The chain is halted (trading/LP-actions/global pause), so deposits must be blocked. */
    data object Halted : InboundAddressResult()

    /** The chain is not present in the THORChain inbound-addresses set. */
    data object Unsupported : InboundAddressResult()

    /** The inbound-addresses fetch failed (network/parse error). */
    data object FetchFailed : InboundAddressResult()
}

/**
 * Owns secured-asset address loading extracted from `DepositFormViewModel`: populating the user's
 * own THORChain address on the SecuredAsset form and resolving THORChain inbound vault addresses
 * (used both by the SecuredAsset deposit path and the Switch sub-form).
 *
 * The repos / API are Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and
 * supplies it (assisted) along with the form-owned [thorAddressFieldState] and the [vaultId] /
 * [selectedToken] accessors so this loader never owns its own scope.
 */
internal class SecuredAssetLoader
@AssistedInject
constructor(
    private val vaultRepository: VaultRepository,
    private val chainAccountAddressRepository: ChainAccountAddressRepository,
    private val thorChainApi: ThorChainApi,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val thorAddressFieldState: TextFieldState,
    @Assisted private val vaultId: () -> String?,
    @Assisted private val selectedToken: () -> Coin,
) {

    /** @see SecuredAssetLoader */
    @AssistedFactory
    interface Factory {
        /** Creates a [SecuredAssetLoader] bound to the given scope, field state and accessors. */
        fun create(
            scope: CoroutineScope,
            thorAddressFieldState: TextFieldState,
            vaultId: () -> String?,
            selectedToken: () -> Coin,
        ): SecuredAssetLoader
    }

    private var securedAssetThorAddressJob: Job? = null

    /** Populates [thorAddressFieldState] with the vault's own THORChain address for the form. */
    fun collectSecuredAssetAddresses() {
        securedAssetThorAddressJob?.cancel()
        securedAssetThorAddressJob =
            scope.safeLaunch(
                onError = { Timber.e(it, "Failed to collect secured asset addresses") }
            ) {
                val vaultId = vaultId() ?: return@safeLaunch
                val vault = vaultRepository.get(vaultId) ?: return@safeLaunch
                val (thorAddress) =
                    chainAccountAddressRepository.getAddress(chain = Chain.ThorChain, vault = vault)

                thorAddressFieldState.setTextAndPlaceCursorAtEnd(thorAddress)
            }
    }

    /** Resolves the THORChain inbound vault address for the currently-selected secured asset. */
    suspend fun fetchSecuredAssetInboundAddress(): InboundAddressResult {
        val chainName = selectedToken().getChainName()
        return fetchThorChainInboundForChain(chainName)
    }

    /**
     * Fetches THORChain's inbound vault address for [chainName] (matched against the THORChain
     * inbound addresses endpoint, case-insensitive) and reports halt/network failure modes so
     * callers can surface a distinct user error instead of silently leaving the destination empty.
     */
    suspend fun fetchThorChainInboundForChain(chainName: String): InboundAddressResult =
        try {
            val inboundAddresses = thorChainApi.getTHORChainInboundAddresses()
            val inboundAddress =
                inboundAddresses.firstOrNull { it.chain.equals(chainName, ignoreCase = true) }
            when {
                inboundAddress == null -> InboundAddressResult.Unsupported
                inboundAddress.halted ||
                    inboundAddress.chainTradingPaused ||
                    inboundAddress.chainLPActionsPaused ||
                    inboundAddress.globalTradingPaused -> InboundAddressResult.Halted
                else -> InboundAddressResult.Available(inboundAddress.address)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.e(e, "Failed to fetch THORChain inbound for %s", chainName)
            InboundAddressResult.FetchFailed
        }
}
