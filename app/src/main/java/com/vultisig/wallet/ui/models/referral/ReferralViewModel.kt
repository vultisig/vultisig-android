package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.models.Chain
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal sealed interface ReferralUiState {
    data object Loading : ReferralUiState

    data object Unavailable : ReferralUiState

    data class Ready(val hasReferral: Boolean) : ReferralUiState
}

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

    val state = MutableStateFlow<ReferralUiState>(ReferralUiState.Loading)
    private var remoteReferral: String? = null
    private var retryCount = 0
    private val maxRetries = 3

    init {
        loadStatus()
    }

    private fun loadStatus() {
        viewModelScope.safeLaunch(
            onError = { t ->
                Timber.e(t, "Failed to load referral status")
                state.value = ReferralUiState.Unavailable
            }
        ) {
            val vaultReferral =
                withContext(Dispatchers.IO) { referralCodeRepository.getReferralCreatedBy(vaultId) }

            val referrals =
                if (vaultReferral.isNullOrEmpty()) {
                    val coin =
                        withContext(Dispatchers.IO) {
                            vaultRepository.get(vaultId)?.coins?.find {
                                it.chain.id == Chain.ThorChain.id && it.isNativeToken
                            }
                        }
                    if (coin == null) {
                        state.value = ReferralUiState.Unavailable
                        return@safeLaunch
                    }

                    // Retry if address is not yet loaded
                    if (coin.address.isBlank()) {
                        if (retryCount < maxRetries) {
                            retryCount++
                            Timber.d(
                                "Coin address not ready, retrying in 500ms (attempt %d/%d)",
                                retryCount,
                                maxRetries,
                            )
                            viewModelScope.launch {
                                delay(500)
                                loadStatus()
                            }
                        } else {
                            Timber.d(
                                "Coin address never became available after %d retries",
                                maxRetries,
                            )
                            state.value = ReferralUiState.Unavailable
                        }
                        return@safeLaunch
                    }

                    withContext(Dispatchers.IO) {
                        thorChainApi.getReferralCodesByAddress(coin.address)
                    }
                } else {
                    listOf(vaultReferral)
                }

            val hasReferral = referrals.isNotEmpty()
            if (hasReferral) {
                val first = referrals.first()
                remoteReferral = first
                withContext(Dispatchers.IO) {
                    referralCodeRepository.saveReferralCreated(vaultId, first)
                }
            }
            state.value = ReferralUiState.Ready(hasReferral = hasReferral)
        }
    }

    fun onCreateOrEditReferral() {
        val current = state.value as? ReferralUiState.Ready ?: return
        viewModelScope.launch {
            if (current.hasReferral) {
                val code = remoteReferral ?: return@launch
                navigator.navigate(Destination.ReferralView(vaultId, code))
            } else {
                navigator.route(Route.ReferralCreation(vaultId))
            }
        }
    }

    fun onMyReferralClick() {
        if (state.value is ReferralUiState.Loading) return
        viewModelScope.launch {
            navigator.navigate(Destination.ReferralView(vaultId, remoteReferral.orEmpty()))
        }
    }

    internal companion object {
        const val MAX_LENGTH_REFERRAL_CODE = 4
    }
}
