package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject


internal data class VaultDetailUiModel(
    val name: String = "",
    val vaultPart: String = "",
    val vaultSize: String = "",
    val pubKeyECDSA: String = "",
    val pubKeyEDDSA: String = "",
    val deviceList: List<DeviceMeta> = emptyList(),
    val libType: String? = "",
)

internal data class DeviceMeta(
    val name: String,
    val isThisDevice: Boolean,
)

@HiltViewModel
internal class VaultDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    @ApplicationContext private val context: Context,
) : ViewModel() {

    private val vaultId: String =
        requireNotNull(savedStateHandle[ARG_VAULT_ID])

    val uiModel = MutableStateFlow(VaultDetailUiModel())

    init {
        viewModelScope.launch {
            Timber.d("loadVaultDetail($vaultId)")
            vaultRepository.get(vaultId)?.let { vault ->
                uiModel.update { it ->
                    it.copy(
                        name = vault.name,
                        vaultPart = vault.getVaultPart().toString(),
                        libType = vault.libType.toString(),
                        vaultSize = vault.signers.size.toString(),
                        pubKeyECDSA = vault.pubKeyECDSA,
                        pubKeyEDDSA = vault.pubKeyEDDSA,
                        deviceList = vault.signers.map {
                            DeviceMeta(
                                name = it,
                                isThisDevice = it == vault.localPartyID
                            )
                        }
                    )
                }
            }
        }
    }
}