package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class ReferralUiState(val isCreateEnabled: Boolean = true)

@HiltViewModel
internal class ReferralViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val referralCodeRepository: ReferralCodeSettingsRepository,
    private val thorChainApi: ThorChainApi,
    private val vaultRepository: VaultRepository,
) : ViewModel() {
    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])

    val state = MutableStateFlow(ReferralUiState())
    private var remoteReferral: String? = null

    init {
        loadStatus()
    }

    private fun loadStatus() {
        viewModelScope.launch {
            val vaultReferral =
                withContext(Dispatchers.IO) { referralCodeRepository.getReferralCreatedBy(vaultId) }

            try {
                val referrals =
                    if (vaultReferral.isNullOrEmpty()) {
                        withContext(Dispatchers.IO) {
                            val coin =
                                vaultRepository.get(vaultId)?.coins?.find {
                                    it.chain.id == Chain.ThorChain.id && it.isNativeToken
                                } ?: error("Coin not found")
                            thorChainApi.getReferralCodesByAddress(coin.address)
                        }
                    } else {
                        listOf(vaultReferral)
                    }
                if (referrals.isNotEmpty()) {
                    state.update { it.copy(isCreateEnabled = false) }
                    remoteReferral = referrals.first()
                    referralCodeRepository.saveReferralCreated(vaultId, remoteReferral!!)
                }
            } catch (t: Throwable) {
                if (t is kotlinx.coroutines.CancellationException) throw t
                Timber.e(t)
            }
        }
    }

    fun onCreateOrEditReferral() {
        viewModelScope.launch {
            if (state.value.isCreateEnabled) {
                navigator.route(Route.ReferralCreation(vaultId))
            } else {
                navigator.navigate(Destination.ReferralView(vaultId, remoteReferral.orEmpty()))
            }
        }
    }

    fun onMyReferralClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.ReferralView(vaultId, remoteReferral.orEmpty()))
        }
    }

    internal companion object {
        const val MAX_LENGTH_REFERRAL_CODE = 4
    }
}
