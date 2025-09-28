package com.vultisig.wallet.ui.models.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.QRCODE_DIRECTORY_NAME_FULL
import com.vultisig.wallet.data.common.saveBitmapToDownloads
import com.vultisig.wallet.data.common.sha256
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.ui.models.ShareVaultQrModel
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.utils.SnackbarFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal data class RegisterVaultUiModel(
    val fileName: String = "",
    val shareVaultQrString: String = "",
    val bitmap: Bitmap? = null,
)

@HiltViewModel
internal class RegisterVaultViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val snackbarFlow: SnackbarFlow,
    @ApplicationContext private val context: Context,
    private val vaultRepository: VaultRepository,
    private val json: Json,
    private val generateQrBitmap: GenerateQrBitmap,
) : ViewModel() {
    val vaultId = requireNotNull(savedStateHandle.get<String>(Destination.ARG_VAULT_ID))
    val permissionChannel = Channel<Boolean>()
    val uiModel = MutableStateFlow(RegisterVaultUiModel())
    private var hasWritePermission by mutableStateOf(
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    )

    init {
        viewModelScope.launch {
            vaultRepository.get(vaultId)?.let { vault ->
                val uid =
                    ("${vault.name} - ${vault.pubKeyECDSA} - " + "${vault.pubKeyEDDSA} - ${vault.hexChainCode}").sha256()

                val shareVaultQrModel = ShareVaultQrModel(
                    name = vault.name,
                    publicKeyEcdsa = vault.pubKeyECDSA,
                    publicKeyEddsa = vault.pubKeyEDDSA,
                    hexChainCode = vault.hexChainCode,
                    uid = uid
                )

                uiModel.update {
                    it.copy(
                        shareVaultQrString = json.encodeToString(shareVaultQrModel),
                        fileName = "VultisigQR-${vault.name}-${shareVaultQrModel.uid.takeLast(3)}.png"
                    )
                }

                if (!hasWritePermission) {
                    permissionChannel.send(true)
                }
            }
        }
    }

    internal fun onPermissionResult(isGranted: Boolean) {
        if (!isGranted) {
            viewModelScope.launch {
                snackbarFlow.showMessage(
                    context.getString(R.string.share_qr_screen_permission_required)
                )
            }
        }
    }

    fun generateBitmap(
        shareVaultQrString: String,
        mainColor: Color,
        backgroundColor: Color,
        logo: Bitmap,
    ) {
        val bitmap = generateQrBitmap(
            shareVaultQrString,
            mainColor,
            backgroundColor,
            logo
        )
        uiModel.update {
            it.copy(
                bitmap = bitmap
            )
        }
    }


    fun saveBitmap() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = context.saveBitmapToDownloads(
                    requireNotNull(uiModel.value.bitmap),
                    requireNotNull(uiModel.value.fileName.takeIf { it.isNotEmpty() })
                )
                uiModel.value.bitmap?.recycle()
                if (uri != null) {
                    showSnackbarMessage()
                }
            } catch (e: Exception) {
                snackbarFlow.showMessage(
                    context.getString(
                        R.string.error_saving_qr_code,
                        e.localizedMessage ?: ""
                    )
                )
            }
        }
    }


    private suspend fun showSnackbarMessage() {
        snackbarFlow.showMessage(
            context.getString(
                R.string.vault_settings_success_backup_file,
                "$QRCODE_DIRECTORY_NAME_FULL/${uiModel.value.fileName}"
            )
        )
    }
}