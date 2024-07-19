package com.vultisig.wallet.ui.models

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.zxing.WriterException
import com.vultisig.wallet.common.buildString
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.models.ShareVaultQrModel
import com.vultisig.wallet.presenter.common.generateQrBitmap
import com.vultisig.wallet.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject

internal data class ShareVaultQrState(
    val shareVaultQrModel: ShareVaultQrModel = ShareVaultQrModel("","","","",""),
    val shareVaultQrString: String? = null,
    val qrBitmapPainter: BitmapPainter = BitmapPainter(Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888).asImageBitmap())
)
@HiltViewModel
internal class ShareVaultQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    vaultRepository: VaultRepository,
    gson: Gson,
): ViewModel() {
    private val vaultId: String? = savedStateHandle[Destination.ARG_VAULT_ID]

    val state = MutableStateFlow(ShareVaultQrState())

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
                    )
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
                val qrBitmap = try {
                    if (logo != null && state.value.shareVaultQrString != null) {
                        generateQrBitmap(
                            requireNotNull(state.value.shareVaultQrString),
                            mainColor,
                            backgroundColor,
                            logo
                        )
                    }else null
                } catch (ex: WriterException) {
                    null
                }?: Bitmap.createBitmap(
                    1,
                    1,
                    Bitmap.Config.ARGB_8888,
                ).apply {
                    eraseColor(0x00000000)
                }

                val bitmapPainter = BitmapPainter(
                    qrBitmap.asImageBitmap(),
                    filterQuality = FilterQuality.None
                )
                state.update {
                    it.copy(
                        qrBitmapPainter = bitmapPainter
                    )
                }
            }
        }
    }

    fun onSaveOrShareClicked() {
        // TODO: Implement onSaveOrShareClick
    }
}