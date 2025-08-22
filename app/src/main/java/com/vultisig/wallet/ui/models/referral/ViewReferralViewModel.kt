package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.api.ThorChainApi
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_REFERRAL_ID
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class ReferralViewUiState(
    val referralFriendCode: String = "",
    val referralVaultCode: String = "",
    val referralVaultExpiration: String = "",
    val vaultName: String = "",
)

@HiltViewModel
internal class ViewReferralViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val referralRepository: ReferralCodeSettingsRepository,
    private val vaultRepository: VaultRepository,
    private val thorChainApi: ThorChainApi,
): ViewModel() {
    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])
    private val vaultReferralCode: String = requireNotNull(savedStateHandle[ARG_REFERRAL_ID])

    val state = MutableStateFlow(ReferralViewUiState())

    init {
        onLoadReferralCodeInfo()
    }

    private fun onLoadReferralCodeInfo() {
        viewModelScope.launch {
            val (vaultName, friendReferralCode) = withContext(Dispatchers.IO) {
                val vaultDeferred =
                    async { vaultRepository.get(vaultId)?.name ?: "Default Vault" }
                val friendReferralDeferred =
                    async { referralRepository.getExternalReferralBy(vaultId) }
                    vaultDeferred.await() to friendReferralDeferred.await()
            }

            state.update {
                it.copy(
                    vaultName = vaultName,
                    referralVaultCode = vaultReferralCode,
                    referralFriendCode = friendReferralCode ?: "",
                )
            }
        }
    }

    fun navigateToStoreFriendReferralBanner() {
        viewModelScope.launch {
            navigator.navigate(Destination.ReferralExternalEdition(vaultId))
        }
    }
}