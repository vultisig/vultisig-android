package com.vultisig.wallet.ui.models

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.data.models.Vault
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateQrBitmap
import com.vultisig.wallet.data.usecases.MakeQrCodeBitmapShareFormat
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject


@HiltViewModel
internal class QrAddressViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val vaultRepository: VaultRepository,
    private val makeQrCodeBitmapShareFormat: MakeQrCodeBitmapShareFormat,
    private val generateQrBitmap: GenerateQrBitmap,
    private val navigator: Navigator<Destination>
) : ViewModel() {
    private val args = savedStateHandle.toRoute<Route.QrAddressScreen>()
    val address = args.address
    val chainName = args.chainName
    private val currentVault: MutableState<Vault?> = mutableStateOf(null)

    val qrBitmapPainter = MutableStateFlow<BitmapPainter?>(null)
    private val shareQrBitmap = MutableStateFlow<Bitmap?>(null)

    init {
        viewModelScope.launch {
            loadQrPainter()
            vaultRepository.get(requireNotNull(args.vaultId))
                ?.let {
                    currentVault.value = it
                }
        }
    }

    private suspend fun loadQrPainter() {
        withContext(Dispatchers.IO) {
            val qrBitmap = generateQrBitmap(address, Color.Black, Color.White, null)
            val bitmapPainter = BitmapPainter(
                qrBitmap.asImageBitmap(), filterQuality = FilterQuality.None
            )
            qrBitmapPainter.value = bitmapPainter
        }
    }

    internal fun saveShareQrBitmap(
        context: Context,
        bitmap: Bitmap,
        color: Int,
        title: String,
        logo: Bitmap,
    ) = viewModelScope.launch {
        val qrBitmap = withContext(Dispatchers.IO) {
            makeQrCodeBitmapShareFormat(context, bitmap, color, logo, title, null)
        }
        shareQrBitmap.value?.recycle()
        shareQrBitmap.value = qrBitmap
    }

    internal fun shareQRCode(activity: Context) {
        val qrBitmap = shareQrBitmap.value ?: return
        activity.share(
            qrBitmap,
            shareFileName(
                requireNotNull(currentVault.value),
                ShareType.TOKENADDRESS
            )
        )
    }

    fun back(){
        viewModelScope.launch {
            navigator.back()
        }
    }
}