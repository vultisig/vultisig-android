package com.vultisig.wallet.ui.models

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
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
import com.vultisig.wallet.common.buildString
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.ShareVaultQrModel
import com.vultisig.wallet.presenter.common.generateQrBitmap
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.NavigationOptions
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
import java.security.MessageDigest
import javax.inject.Inject

internal data class ShareVaultQrState(
    val shareVaultQrModel: ShareVaultQrModel = ShareVaultQrModel("","","","",""),
    val shareVaultQrString: String? = null,
    val qrBitmapPainter: BitmapPainter = BitmapPainter(
        Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap()
    ),
    val toShareBitmap: Bitmap? = null,
    val fileName: String? = null,
)

@HiltViewModel
internal class ShareVaultQrViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val snackbarFlow: SnackbarFlow,
    @ApplicationContext private val context: Context,
    private val gson: Gson,
): ViewModel() {
    private val vaultId: String? = savedStateHandle[Destination.ARG_VAULT_ID]

    val state = MutableStateFlow(ShareVaultQrState())

    private var hasWritePermission by mutableStateOf(
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    )

    private val permissionChannel = Channel<Boolean>()
    val permissionFlow = permissionChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            vaultRepository.get(requireNotNull(vaultId))?.let { vault ->
                val messageDigest = MessageDigest.getInstance("SHA-256")
                val uid = "${vault.name} - ${vault.pubKeyECDSA} - " +
                        "${vault.pubKeyEDDSA} - ${vault.hexChainCode}"
                messageDigest.update(uid.toByteArray())
                val uidEncoded = messageDigest.digest().buildString()
                val shareVaultQrModel = ShareVaultQrModel(
                    name = vault.id,
                    public_key_ecdsa = vault.pubKeyECDSA,
                    public_key_eddsa = vault.pubKeyEDDSA,
                    hex_chain_code = vault.hexChainCode,
                    uid = uidEncoded
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

    internal fun loadLogoIcon(
        mainColor: Color,
        backgroundColor: Color,
        logo : Bitmap? = null
    ) {
        viewModelScope.launch {
            withContext(Dispatchers.IO){
                val qrBitmap = generateBitmap(
                    logo = logo,
                    mainColor = mainColor,
                    backgroundColor = backgroundColor)

                val bitmapPainter = BitmapPainter(
                    qrBitmap.asImageBitmap(),
                    filterQuality = FilterQuality.None
                )
                state.update {
                    it.copy(
                        qrBitmapPainter = bitmapPainter
                    )
                }
                state.update {
                    it.copy(
                        toShareBitmap = generateBitmap(
                            logo = logo,
                            mainColor = mainColor,
                            backgroundColor = Color(0xff0D86BB)
                        )
                    )
                }
            }
        }
    }

    private fun generateBitmap(
        logo: Bitmap?,
        mainColor: Color,
        backgroundColor: Color
    ): Bitmap {
        val qrBitmap = try {
            if (logo != null && state.value.shareVaultQrString != null) {
                generateQrBitmap(
                    requireNotNull(state.value.shareVaultQrString),
                    mainColor,
                    backgroundColor,
                    logo
                )
            } else null
        } catch (ex: WriterException) {
            null
        } ?: Bitmap.createBitmap(
            1,
            1,
            Bitmap.Config.ARGB_8888,
        ).apply {
            eraseColor(0x00000000)
        }
        return qrBitmap
    }

    fun onPermissionResult(isGranted: Boolean) {
        if (!isGranted) {
            viewModelScope.launch {
                snackbarFlow.showMessage(
                    context.getString(R.string.share_qr_screen_permission_required)
                )
            }
        }
    }

    fun onSaveOrShareClicked() {
        // TODO: Implement onSaveOrShareClick
    }
}