package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination.VaultSettings.Companion.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject


internal data class VaultDetailUiModel(
    val name:String = "",
    val pubKeyECDSA:String = "",
    val pubKeyEDDSA:String = "",
    val deviceList: List<String> = emptyList()
)

@HiltViewModel
internal class VaultDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val vaultId: String =
        savedStateHandle.get<String>(ARG_VAULT_ID)!!

    val uiModel = MutableStateFlow(VaultDetailUiModel())

    init {
        viewModelScope.launch {
            vaultRepository.get(vaultId)?.let { vault ->
                uiModel.update { it ->
                    it.copy(
                        name = vault.name,
                        pubKeyECDSA = vault.pubKeyECDSA,
                        pubKeyEDDSA = vault.pubKeyEDDSA,
                        deviceList = vault.signers.map {
                            if (it == vault.localPartyID) {
                                context.getString(
                                    R.string.vault_detail_this_device,
                                    it
                                )
                            } else {
                                it
                            }
                        }
                    )
                }
            }
        }
    }
}