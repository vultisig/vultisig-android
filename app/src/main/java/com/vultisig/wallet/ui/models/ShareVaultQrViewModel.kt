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
import androidx.core.graphics.createBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.google.zxing.WriterException
import com.vultisig.wallet.R
import com.vultisig.wallet.data.common.QRCODE_DIRECTORY_NAME_FULL
import com.vultisig.wallet.data.common.saveBitmapToDownloads
import com.vultisig.wallet.data.common.sha256
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

internal data class ShareVaultQrState(
    val shareVaultQrModel: ShareVaultQrModel = ShareVaultQrModel("", "", "", "", ""),
    val shareVaultQrString: String? = null,
    val fileName: String? = null,
    val fileUri: Uri? = null,
)

@Serializable
internal data class ShareVaultQrModel(
    val uid: String,
    val name: String,
    val publicKeyEcdsa: String,
    val publicKeyEddsa: String,
    val hexChainCode: String,
)

@HiltViewModel
internal class ShareVaultQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val navigator: Navigator<Destination>,
    private val vaultRepository: VaultRepository,
    private val snackbarFlow: SnackbarFlow,
    @ApplicationContext private val context: Context,
    private val json: Json,
    private val generateQrBitmap: GenerateQrBitmap,
) : ViewModel() {
    private val vaultId: String = savedStateHandle.toRoute<Route.ShareVaultQr>().vaultId

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

    internal fun loadQrCodePainter(
        mainColor: Color,
        backgroundColor: Color,
        logo: Bitmap? = null,
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val qrBitmap = generateBitmap(
                    logo = logo, mainColor = mainColor, backgroundColor = backgroundColor
                ) ?: createBitmap(1, 1)

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

    internal fun save() {
        saveBitmap(toShare = false)
    }

    internal fun share() {
        saveBitmap(toShare = true)
    }

    private fun saveBitmap(toShare: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val uri = context.saveBitmapToDownloads(
                    requireNotNull(shareQrBitmap.value),
                    requireNotNull(state.value.fileName)
                )
                shareQrBitmap.value?.recycle()
                if (toShare) {
                    state.update {
                        it.copy(
                            fileUri = uri
                        )
                    }
                } else if (uri != null) {
                    showSnackbarMessage()
                    navigator.navigate(Destination.Back)
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
                "$QRCODE_DIRECTORY_NAME_FULL/${state.value.fileName}"
            )
        )
    }

    internal fun showSnackbarSavedMessage() {
        viewModelScope.launch {
            showSnackbarMessage()
        }
    }
}