package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.ui.navigation.Destination.Companion.ARG_VAULT_ID
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareVaultDetailName
import dagger.hilt.android.lifecycle.HiltViewModel
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

    fun takeScreenShot(
        graphicsLayer: GraphicsLayer,
        context: Context,
    ) {
        viewModelScope.launch {
            try {
                val bitmap = graphicsLayer.toImageBitmap().asAndroidBitmap()
                context.share(
                    bitmap = bitmap,
                    fileName = shareVaultDetailName(
                        vaultName = uiModel.value.name,
                        vaultPart = uiModel.value.vaultPart,
                    )
                )
                bitmap.recycle()
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "Failed to capture screenshot"
                )
            }
        }
    }
}