package com.vultisig.wallet.ui.screens.settings

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
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
import com.vultisig.wallet.ui.theme.Theme
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
internal fun FormRegister(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    Card(
        modifier = modifier.border(width = 1.dp, colors.backgrounds.tertiary, shape = RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = colors.oxfordBlue600Main,
        ),
        shape = RoundedCornerShape(16.dp),

        content = content,
    )
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
            painter = painterResource(id = R.drawable.register_vaults),
            contentDescription = "Vultisig logo",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier
                .padding(1.dp)
                .width(254.dp)
                .height(161.dp)
        )
        UiSpacer(size = 48.dp)
        Text(
            modifier = Modifier
                .fillMaxWidth(),
            text = "Register guide",
            color = colors.text.primary,
            style = Theme.brockmann.headings.largeTitle,
        )
        UiSpacer(size = 40.dp)
        FormRegister(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(com.vultisig.wallet.R.drawable.qr_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(16.dp)
                )
                Text(
                    text = stringResource(R.string.register_vault_screen_step_1),
                    color = colors.text.primary,
                    style = Theme.brockmann.supplementary.footnote,
                )
            }
        }
        UiSpacer(size = 16.dp)
        FormRegister(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(com.vultisig.wallet.R.drawable.web_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(16.dp)
                )
                Text(
                    text = stringResource(R.string.register_vault_screen_step_2_go_to),
                    color = colors.text.primary,
                    style = Theme.brockmann.supplementary.footnote,
                )

                Text(
                    modifier = Modifier
                        .clickOnce(onClick = onVultisigLinkClick),
                    text = stringResource(R.string.register_vault_screen_step_2_vultisig_web),
                    style = Theme.brockmann.supplementary.footnote,
                    color = colors.alerts.success,
                    textDecoration = TextDecoration.Underline,

                )

            }

        }
        UiSpacer(size = 16.dp)
        FormRegister(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(com.vultisig.wallet.R.drawable.upload_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(16.dp)
                )
                Text(
                    text = stringResource(R.string.register_vault_screen_step_3),
                    color = colors.neutral0,
                    style = Theme.brockmann.supplementary.footnote,
                )
            }
        }
            UiSpacer(size = 16.dp)
            FormRegister(
                modifier = Modifier
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(com.vultisig.wallet.R.drawable.points_icon),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.height(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.register_vault_screen_step_4),
                        color = colors.neutral0,
                        style = Theme.brockmann.supplementary.footnote,
                    )
                }
            }
        }

}

@Preview
@Composable
private fun RegisterVaultScreenPreview() {
    RegisterVaultScreen {}
}

