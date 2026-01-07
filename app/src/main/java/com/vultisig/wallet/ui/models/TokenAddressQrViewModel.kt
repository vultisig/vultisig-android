package com.vultisig.wallet.ui.models

import android.content.Context
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.GenerateAccountQrUseCase
import com.vultisig.wallet.data.usecases.QrBitmapData
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import javax.inject.Inject

internal data class TokenAddressQr(
    val chainName: String = "",
    val chainAddress: String = "",
    val qrCode: BitmapPainter? = null,
)

@HiltViewModel
internal class TokenAddressQrViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val generateAccountQrUseCase: GenerateAccountQrUseCase,
    private val vaultRepository: VaultRepository,
    private val navigator: Navigator<Destination>,
    private val snackbarFlow: SnackbarFlow,
    @param:ApplicationContext private val context: Context,
) : ViewModel() {
    val args = savedStateHandle.toRoute<Route.AddressQr>()
    val uiState = MutableStateFlow(TokenAddressQr())
    lateinit var qrBitmapData: QrBitmapData

    fun loadData() {
        viewModelScope.launch {
            qrBitmapData = generateAccountQrUseCase(
                args.address,
                args.logo
            )
            uiState.update {
                it.copy(
                    chainName = args.name,
                    chainAddress = args.address,
                    qrCode = qrBitmapData.bitmapPainter
                )
            }
        }
    }

    fun shareQRCode(
        graphicsLayer: GraphicsLayer,
        context: Context,
    ) {
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    graphicsLayer.toImageBitmap().asAndroidBitmap()
                }
                try {
                    withContext(Dispatchers.Main) {
                        context.share(
                            bitmap = bitmap,
                            fileName = shareFileName(
                                requireNotNull(vaultRepository.get(args.vaultId)),
                                ShareType.TOKENADDRESS
                            )
                        )
                    }
                } finally {
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                Timber.e(
                    e,
                    "Failed to capture and share qr address screenshot"
                )
            }

            back()
        }
    }

    fun copy() {
        viewModelScope.launch {
            back()
            snackbarFlow.showMessage(
                context.getString(
                    R.string.chain_token_screen_address_copied,
                    uiState.value.chainName
                )
            )
        }
    }

    fun back() {
        viewModelScope.launch {
            navigator.back()
        }
    }
}