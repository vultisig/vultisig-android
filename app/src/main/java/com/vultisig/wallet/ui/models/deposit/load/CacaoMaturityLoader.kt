package com.vultisig.wallet.ui.models.deposit.load

import com.vultisig.wallet.R
import com.vultisig.wallet.data.usecases.GetMayaCacaoMaturityStatusUseCase
import com.vultisig.wallet.data.usecases.MayaCacaoMaturityStatus
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.utils.UiText
import com.vultisig.wallet.ui.utils.cacaoUnlocksInUiText
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

/**
 * Owns MAYA CACAO unstake-maturity loading extracted from `DepositFormViewModel`: fetches the
 * maturity status for the user's MAYA address and reports the resulting maturity flag and "unlocks
 * in" text back to the ViewModel.
 *
 * The use case is Hilt-injected here; the ViewModel keeps `viewModelScope` ownership and supplies
 * it (assisted) along with the [onResult] callback so this loader never owns its own scope or VM
 * state.
 */
internal class CacaoMaturityLoader
@AssistedInject
constructor(
    private val getMayaCacaoMaturityStatus: GetMayaCacaoMaturityStatusUseCase,
    @Assisted private val scope: CoroutineScope,
    @Assisted private val onResult: (isMature: Boolean, unlocksInText: UiText?) -> Unit,
) {

    /** @see CacaoMaturityLoader */
    @AssistedFactory
    interface Factory {
        /** Creates a [CacaoMaturityLoader] bound to the given scope and result callback. */
        fun create(
            scope: CoroutineScope,
            onResult: (isMature: Boolean, unlocksInText: UiText?) -> Unit,
        ): CacaoMaturityLoader
    }

    private var cacaoMaturityJob: Job? = null

    /**
     * Loads the CACAO unstake-maturity status for [addressValue] and forwards it via [onResult].
     */
    fun loadCacaoMaturityStatus(addressValue: String) {
        cacaoMaturityJob?.cancel()
        cacaoMaturityJob =
            scope.safeLaunch {
                val status = getMayaCacaoMaturityStatus(addressValue)
                onResult(status.isMature, status.toUnlocksInText())
            }
    }

    private fun MayaCacaoMaturityStatus.toUnlocksInText(): UiText? =
        when {
            isUnknown -> UiText.StringResource(R.string.unstake_cacao_maturity_check_failed)
            isMature || remainingSeconds <= 0L -> null
            else -> cacaoUnlocksInUiText(remainingSeconds)
        }
}
