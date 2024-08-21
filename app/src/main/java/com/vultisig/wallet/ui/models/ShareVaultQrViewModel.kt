package com.vultisig.wallet.ui.models

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.core.content.ContextCompat
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.zxing.WriterException
import com.vultisig.wallet.R
import com.vultisig.wallet.common.QRCODE_DIRECTORY_NAME_FULL
import com.vultisig.wallet.common.saveBitmapToDownloads
import com.vultisig.wallet.common.sha256
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.ShareVaultQrModel
import com.vultisig.wallet.presenter.common.generateQrBitmap
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.utils.SnackbarFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal data class ShareVaultQrState(
    val shareVaultQrModel: ShareVaultQrModel = ShareVaultQrModel("", "", "", "", ""),
    val shareVaultQrString: String? = null,
    val fileName: String? = null,
    val fileUri: Uri? = null,
)

@HiltViewModel
internal class ShareVaultQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val snackbarFlow: SnackbarFlow,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
) : ViewModel() {
    private val vaultId: String? = savedStateHandle[Destination.ARG_VAULT_ID]

    val state = MutableStateFlow(ShareVaultQrState())

    val qrBitmapPainter = MutableStateFlow<BitmapPainter?>(null)
    private val shareQrBitmap = MutableStateFlow<Bitmap?>(null)


    private var hasWritePermission by mutableStateOf(
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    )

    private val permissionChannel = Channel<Boolean>()
    val permissionFlow = permissionChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            vaultRepository.get(requireNotNull(vaultId))?.let { vault ->
                val uid =
                    ("${vault.name} - ${vault.pubKeyECDSA} - " +
                            "${vault.pubKeyEDDSA} - ${vault.hexChainCode}").sha256()
                val shareVaultQrModel = ShareVaultQrModel(
                    name = vault.name,
                    publicKeyEcdsa = vault.pubKeyECDSA,
                    publicKeyEddsa = vault.pubKeyEDDSA,
                    hexChainCode = vault.hexChainCode,
                    uid = uid
                )
                state.update {
                    it.copy(
                        shareVaultQrModel = shareVaultQrModel,
                        shareVaultQrString = gson.toJson(shareVaultQrModel),
                        fileName = "Vultisig-${vault.name}-${vault.id.takeLast(3)}.png"
                    )
                }
                if (!hasWritePermission) {
                    permissionChannel.send(true)
                }
            }
        }
    }

    internal fun loadQrCode(
        mainColor: Color,
        backgroundColor: Color,
        logo: Bitmap? = null,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val qrBitmap = generateBitmap(
                    logo = logo, mainColor = mainColor, backgroundColor = backgroundColor
                ) ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

                val bitmapPainter = BitmapPainter(
                    qrBitmap.asImageBitmap(), filterQuality = FilterQuality.None
                )
                qrBitmapPainter.value = bitmapPainter
                logo?.recycle()
            }
        }
    }

    private fun generateBitmap(
        logo: Bitmap?,
        mainColor: Color,
        backgroundColor: Color,
    ): Bitmap? {
        val qrBitmap = try {
            if (logo != null && state.value.shareVaultQrString != null) {
                generateQrBitmap(
                    requireNotNull(state.value.shareVaultQrString), mainColor, backgroundColor, logo
                )
            } else null
        } catch (ex: WriterException) {
            null
        }
        return qrBitmap
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

    internal fun saveShareQrBitmap(bitmap: Bitmap) {
        shareQrBitmap.value?.recycle()
        shareQrBitmap.value = bitmap
    }

    internal fun onSaveClicked() {
        viewModelScope.launch(Dispatchers.IO) {
            val uri = context.saveBitmapToDownloads(
                requireNotNull(shareQrBitmap.value),
                requireNotNull(state.value.fileName)
            )
            shareQrBitmap.value?.recycle()
            state.update {
                it.copy(
                    fileUri = uri
                )
            }
            if (uri != null) {
                snackbarFlow.showMessage(
                    context.getString(
                        R.string.vault_settings_success_backup_file,
                        "$QRCODE_DIRECTORY_NAME_FULL/${state.value.fileName}"
                    )
                )
            }
        }
    }
}