package com.vultisig.wallet.ui.models.referral

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.isFastVault
import com.vultisig.wallet.data.repositories.ReferralCodeSettingsRepository
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.navigation.Navigator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    private val vaultId: String = requireNotNull(savedStateHandle[ARG_VAULT_ID])

    private val _state = MutableStateFlow(ReferralVaultListUiState())
    val state: StateFlow<ReferralVaultListUiState> = _state.asStateFlow()

    init {
        loadVaults()
    }

    private fun loadVaults() {
        viewModelScope.launch {
            try {
                val vaults = withContext(Dispatchers.IO) {
                    vaultRepository.getAll()
                }
                val selectedVault = withContext(Dispatchers.IO) {
                    referralCodeRepository.getCurrentVaultId()
                }

                val vaultToSelect = when {
                    !selectedVault.isNullOrEmpty() -> selectedVault
                    else -> vaultId
                }

                val vaultItems = vaults.map { vault ->
                    val signerIndex = vault.signers.indexOf(vault.localPartyID)
                    val partNumber = if (signerIndex != -1) signerIndex + 1 else vault.signers.size
                    val signingInfo = "Part $partNumber of ${vault.signers.size}"

                    VaultItem(
                        id = vault.id,
                        name = vault.name,
                        isSelected = vault.id == vaultToSelect,
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

    fun onVaultClick(vaultId: String) {
        viewModelScope.launch {
            referralCodeRepository.setCurrentVaultId(vaultId)

            navigator.navigate(Destination.Back)
        }
    }

    fun onBackClick() {
        viewModelScope.launch {
            navigator.navigate(Destination.Back)
        }
    }
}