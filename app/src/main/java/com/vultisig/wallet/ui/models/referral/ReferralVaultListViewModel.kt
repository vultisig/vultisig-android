package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class ReferralVaultListUiState(
    val vaults: List<VaultItem> = emptyList(),
    val error: String? = null,
)

internal data class VaultItem(
    val id: String,
    val name: String,
    val isSelected: Boolean = false,
    val signingInfo: String = "",
    val isFastVault: Boolean = false,
)

@HiltViewModel
internal class ReferralVaultListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val referralCodeRepository: ReferralCodeSettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReferralVaultListUiState())
    val state: StateFlow<ReferralVaultListUiState> = _state.asStateFlow()

    init {
        loadVaults()
    }

    private fun loadVaults() {
        viewModelScope.launch {
            try {
                val vaults = vaultRepository.getAll()
                val vaultItems = vaults.map { vault ->
                    val signerIndex = vault.signers.indexOf(vault.localPartyID)
                    val partNumber = if (signerIndex != -1) signerIndex + 1 else vault.signers.size
                    val signingInfo = "Part $partNumber of ${vault.signers.size}"

                    VaultItem(
                        id = vault.id,
                        name = vault.name,
                        isSelected = true,
                        signingInfo = signingInfo,
                        isFastVault = vault.isFastVault(),
                    )
                }

                _state.update {
                    it.copy(
                        vaults = vaultItems,
                        error = null
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(
                        error = e.message ?: "Failed to load vaults"
                    )
                }
            }
        }
    }

    fun onVaultClick(vaultId: String, hasReferralCode: Boolean) {
        viewModelScope.launch {
            //navigator.navigate(Destination.ViewReferral(vaultId, referralCode))
        }
    }

    fun onBackClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}