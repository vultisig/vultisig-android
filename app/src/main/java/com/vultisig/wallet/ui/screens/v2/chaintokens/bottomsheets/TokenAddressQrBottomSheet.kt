package com.vultisig.wallet.ui.screens.v2.chaintokens.bottomsheets

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.vultisig.wallet.R
import com.vultisig.wallet.data.repositories.VaultRepository
import com.vultisig.wallet.data.usecases.QrBitmapData
import com.vultisig.wallet.data.usecases.GenerateAccountQrUseCase
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonSize
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.v2.bottomsheets.DottyBottomSheet
import com.vultisig.wallet.ui.navigation.Destination
import com.vultisig.wallet.ui.navigation.Navigator
import com.vultisig.wallet.ui.navigation.Route
import com.vultisig.wallet.ui.navigation.back
import com.vultisig.wallet.ui.screens.v2.chaintokens.components.QrContainer
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.ShareType
import com.vultisig.wallet.ui.utils.SnackbarFlow
import com.vultisig.wallet.ui.utils.VsClipboardService
import com.vultisig.wallet.ui.utils.share
import com.vultisig.wallet.ui.utils.shareFileName
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

    init {
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

    fun shareQRCode(context: Context) {
        viewModelScope.launch {
            context.share(
                qrBitmapData.bitmap ?: return@launch,
                shareFileName(
                    requireNotNull(vaultRepository.get(args.vaultId)),
                    ShareType.TOKENADDRESS
                )
            )
        }
    }

    fun copy(){
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


@Composable
internal fun TokenAddressQrBottomSheet(
    viewModel: TokenAddressQrViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    TokenAddressQrBottomSheet(
        chainName = uiState.chainName,
        chainAddress = uiState.chainAddress,
        qrBitmapPainter = uiState.qrCode,
        onDismiss = viewModel::back,
        onShareQrClick = {
            viewModel.shareQRCode(context)
        },
        onCopyAddressClick = {
            VsClipboardService.copy(
                context,
                uiState.chainAddress
            )
            viewModel.copy()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TokenAddressQrBottomSheet(
    chainName: String,
    chainAddress: String,
    qrBitmapPainter: BitmapPainter?,
    onDismiss: () -> Unit = {},
    onShareQrClick: () -> Unit = {},
    onCopyAddressClick: () -> Unit = {},
) {

    DottyBottomSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .padding(30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {

            QrContainer(
                chainName = chainName,
                qrCode = qrBitmapPainter
            )

            UiSpacer(
                size = 24.dp
            )

            Text(
                text = chainAddress,
                style = Theme.brockmann.supplementary.footnote,
                color = Theme.colors.text.primary,
                modifier = Modifier.width(232.dp),
                textAlign = TextAlign.Center
            )

            UiSpacer(
                size = 32.dp
            )

            Row {
                VsButton(
                    variant = VsButtonVariant.Tertiary,
                    size = VsButtonSize.Small,
                    label = stringResource(R.string.share_vault_qr_share),
                    onClick = onShareQrClick,
                    modifier = Modifier
                        .weight(1f)
                )
                UiSpacer(
                    size = 4.dp
                )
                VsButton(
                    variant = VsButtonVariant.Primary,
                    size = VsButtonSize.Small,
                    label = stringResource(R.string.copy_address),
                    onClick = onCopyAddressClick,
                    modifier = Modifier
                        .weight(1f)
                )

            }

            UiSpacer(
                size = 12.dp
            )
        }
    }

}

@Preview
@Composable
private fun TokenAddressQrBottomSheetPreview() {
    Row {
        VsButton(
            variant = VsButtonVariant.Tertiary,
            size = VsButtonSize.Small,
            label = stringResource(R.string.share_vault_qr_share),
            onClick = {},
            modifier = Modifier
                .weight(1f)
        )
        UiSpacer(
            size = 8.dp
        )
        VsButton(
            variant = VsButtonVariant.Primary,
            size = VsButtonSize.Medium,
            label = stringResource(R.string.copy_address),
            onClick = {  },
            modifier = Modifier
                .weight(1f)
        )

    }
}