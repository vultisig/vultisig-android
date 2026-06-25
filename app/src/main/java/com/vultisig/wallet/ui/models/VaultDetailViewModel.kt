package com.vultisig.wallet.ui.models

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.SigningLibType
import com.vultisig.wallet.data.models.getVaultPart
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.utils.safeLaunch
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.usecases.ShareBitmapUseCase
import com.vultisig.wallet.ui.utils.shareVaultDetailName
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

internal data class VaultDetailUiModel(
    val name: String = "",
    val vaultPart: String = "",
    val vaultSize: String = "",
    val pubKeyECDSA: String = "",
    val pubKeyEDDSA: String = "",
    val pubKeyMLDSA: String = "",
    val deviceList: List<DeviceMeta> = emptyList(),
    val libType: String? = "",
)

internal data class DeviceMeta(val name: String, val isThisDevice: Boolean)

@HiltViewModel
internal class VaultDetailViewModel
@Inject
constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val shareBitmap: ShareBitmapUseCase,
) : ViewModel() {

    private val vaultId: String = savedStateHandle.toRoute<Route.Details>().vaultId

    val uiModel = MutableStateFlow(VaultDetailUiModel())

    init {
        viewModelScope.safeLaunch {
            Timber.d("loadVaultDetail($vaultId)")
            vaultRepository.get(vaultId)?.let { vault ->
                uiModel.update { it ->
                    it.copy(
                        name = vault.name,
                        vaultPart = vault.getVaultPart().toString(),
                        libType =
                            when (vault.libType) {
                                SigningLibType.KeyImport -> "DKLS-Imported"
                                else -> vault.libType.toString()
                            },
                        vaultSize = vault.signers.size.toString(),
                        pubKeyECDSA = vault.pubKeyECDSA,
                        pubKeyEDDSA = vault.pubKeyEDDSA,
                        pubKeyMLDSA = vault.pubKeyMLDSA,
                        deviceList =
                            vault.signers.map {
                                DeviceMeta(name = it, isThisDevice = it == vault.localPartyID)
                            },
                    )
                }
            }
        }
    }

    fun takeScreenShot(graphicsLayer: GraphicsLayer) {
        viewModelScope.launch {
            try {
                val bitmap =
                    withContext(Dispatchers.Default) {
                        graphicsLayer.toImageBitmap().asAndroidBitmap()
                    }
                try {
                    shareBitmap(
                        bitmap = bitmap,
                        fileName =
                            shareVaultDetailName(
                                vaultName = uiModel.value.name,
                                vaultPart = uiModel.value.vaultPart,
                            ),
                    )
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Timber.e(e, "Failed to capture and share vault screenshot")
            }
        }
    }
}
