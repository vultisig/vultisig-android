package com.vultisig.wallet.ui.screens.settings

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.settings.RegisterVaultViewModel
import com.vultisig.wallet.ui.theme.Theme.colors
import com.vultisig.wallet.ui.theme.Theme.montserrat
import com.vultisig.wallet.ui.utils.VsAuxiliaryLinks
import com.vultisig.wallet.ui.utils.WriteFilePermissionHandler
import com.vultisig.wallet.ui.utils.VsUriHandler
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
internal fun RegisterVaultScreen(
    navController: NavHostController,
    viewModel: RegisterVaultViewModel = hiltViewModel(),
) {
    val uriHandler = VsUriHandler()
    val mainColor = colors.neutral0
    val backgroundColor = colors.transparent
    val context = LocalContext.current
    val uiModel by viewModel.uiModel.collectAsState()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
        WriteFilePermissionHandler(
            viewModel.permissionChannel.receiveAsFlow(),
            viewModel::onPermissionResult
        )
    }

    LaunchedEffect(uiModel.shareVaultQrString) {
        if (uiModel.shareVaultQrString.isNotEmpty()) {
            viewModel.generateBitmap(
                uiModel.shareVaultQrString,
                mainColor,
                backgroundColor,
                BitmapFactory.decodeResource(
                    context.resources,
                    R.drawable.ic_qr_vultisig
                )
            )
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.oxfordBlue800),
        topBar = {
            TopBar(
                navController = navController,
                centerText = stringResource(R.string.register_vault_screen_title),
                startIcon = R.drawable.ic_caret_left,
            )
        },
        bottomBar = {
            VsButton(
                onClick = viewModel::saveBitmap,
                label = stringResource(R.string.register_vault_screen_save_qr_code),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
            )
        }
    ) {
        Box(modifier = Modifier.padding(it)) {
            RegisterVaultScreen {
                uriHandler.openUri(VsAuxiliaryLinks.AIRDROP)
            }

        }
    }
}


@Composable
private fun RegisterVaultScreen(
    onVultisigLinkClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Image(
            painter = painterResource(id = com.vultisig.wallet.R.drawable.register_vaults),
            contentDescription = "Vultisig logo",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.width(256.dp),
        )
        UiSpacer(size = 32.dp)
        FormCard(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.register_vault_screen_step_1),
                    color = colors.neutral0,
                    style = montserrat.body2,
                )
            }
        }
        UiSpacer(size = 16.dp)
        FormCard(modifier = Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 16.dp),
                    text = stringResource(R.string.register_vault_screen_step_2_go_to),
                    color = colors.neutral0,
                    style = montserrat.body2,
                )

                Text(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickOnce(onClick = onVultisigLinkClick)
                        .background(colors.oxfordBlue400)
                        .padding(vertical = 8.dp, horizontal = 30.dp),
                    text = stringResource(R.string.register_vault_screen_step_2_vultisig_web),
                    style = montserrat.body2,
                    color = colors.turquoise600Main,
                )

            }

        }
        UiSpacer(size = 16.dp)
        FormCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.register_vault_screen_step_3),
                modifier = Modifier.padding(16.dp),
                color = colors.neutral0,
                style = montserrat.body2,
            )
        }
        UiSpacer(size = 16.dp)
        FormCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.register_vault_screen_step_4),
                modifier = Modifier.padding(16.dp),
                color = colors.neutral0,
                style = montserrat.body2,
            )
        }
    }
}

@Preview
@Composable
private fun RegisterVaultScreenPreview() {
    RegisterVaultScreen {}
}

