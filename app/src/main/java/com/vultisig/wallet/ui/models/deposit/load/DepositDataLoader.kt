package com.vultisig.wallet.ui.models.deposit.load

import com.vultisig.wallet.data.models.Address
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.AccountsRepository
import com.vultisig.wallet.ui.models.deposit.DepositOption
import com.vultisig.wallet.ui.screens.v2.defi.model.DeFiNavActions
import com.vultisig.wallet.ui.screens.v2.defi.model.parseDepositType
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns the core address/metadata-loading primitives extracted from `DepositFormViewModel`:
 * resolving the vault's address for the active chain into the shared [address] flow and consuming
 * the pending `depositTypeAction` deep-link into the matching [DepositOption].
 *
 * The repository is Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and supplies
 * it (assisted) along with the shared [address] flow, the [depositTypeActionProvider] /
 * [clearDepositTypeAction] accessors and the [selectDepositOption] callback so this loader never
 * owns its own scope or VM state.
 */
internal class DepositDataLoader
@AssistedInject
constructor(
    private val accountsRepository: AccountsRepository,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val address: MutableStateFlow<Address?>,
    @Assisted private val depositTypeActionProvider: () -> String?,
    @Assisted private val clearDepositTypeAction: () -> Unit,
    @Assisted private val selectDepositOption: (DepositOption) -> Unit,
) {

    /** @see DepositDataLoader */
    @AssistedFactory
    interface Factory {
        /**
         * Creates a [DepositDataLoader] bound to the given scope, shared address flow, accessors
         * and callback.
         */
        fun create(
            scope: CoroutineScope,
            address: MutableStateFlow<Address?>,
            depositTypeActionProvider: () -> String?,
            clearDepositTypeAction: () -> Unit,
            selectDepositOption: (DepositOption) -> Unit,
        ): DepositDataLoader
    }

    private var addressJob: Job? = null

    /**
     * Resolves the [vaultId]'s address for [chain] and writes each emission into the shared
     * [address] flow, cancelling any previous in-flight resolution first so a superseded load can't
     * overwrite a fresher one.
     */
    fun loadAddress(vaultId: String, chain: Chain) {
        addressJob?.cancel()
        addressJob =
            scope.launch {
                try {
                    accountsRepository.loadAddress(vaultId, chain).collect { loadedAddress ->
                        address.value = loadedAddress
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    Timber.e(e)
                }
            }
    }

    /**
     * Consumes the pending `depositTypeAction` deep-link (read-once, then cleared) and selects the
     * matching [DepositOption], defaulting to [DepositOption.Bond]. No-op when no action is
     * pending.
     */
    fun setMetadataInfo() {
        val action = depositTypeActionProvider()?.takeIf { it.isNotEmpty() } ?: return
        clearDepositTypeAction()

        val depositOption =
            when (parseDepositType(action)) {
                DeFiNavActions.BOND -> DepositOption.Bond
                DeFiNavActions.UNBOND -> DepositOption.Unbond
                DeFiNavActions.STAKE_CACAO -> DepositOption.AddCacaoPool
                DeFiNavActions.UNSTAKE_CACAO -> DepositOption.RemoveCacaoPool
                DeFiNavActions.ADD_LP -> DepositOption.AddLiquidity
                DeFiNavActions.REMOVE_LP -> DepositOption.RemoveLiquidity
                else -> DepositOption.Bond
            }
        selectDepositOption(depositOption)
    }
}
