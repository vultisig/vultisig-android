package com.vultisig.wallet.ui.screens.settings

import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.TopBar
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.clickOnce
import com.vultisig.wallet.ui.components.library.form.FormCard
import com.vultisig.wallet.ui.models.settings.RegisterVaultViewModel
import com.vultisig.wallet.ui.theme.Theme.colors
import com.vultisig.wallet.ui.theme.Theme.montserrat
import com.vultisig.wallet.ui.utils.WriteFilePermissionHandler
import kotlinx.coroutines.flow.receiveAsFlow

@Composable
internal fun RegisterVaultScreen(
    navController: NavHostController,
    viewModel: RegisterVaultViewModel = hiltViewModel(),
) {
    val uriHandler = LocalUriHandler.current
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
                startIcon = R.drawable.caret_left,
            )
        }
    ) {
        Box(modifier = Modifier.padding(it)) {
            RegisterVaultScreen(
                onVultisigLinkClick = {
                    uriHandler.openUri("https://airdrop.vultisig.com")
                },
                onSaveVaultClick = viewModel::saveBitmap
            )
        }
    }
}


@Composable
private fun RegisterVaultScreen(
    onVultisigLinkClick: () -> Unit,
    onSaveVaultClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        UiSpacer(weight = 1f)
        Image(
            painter = painterResource(id = R.drawable.vultisig),
            contentDescription = "Vultisig logo",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth(0.7f)
        )
        UiSpacer(size = 32.dp)
        FormCard(
            modifier = Modifier
                .fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.register_vault_screen_step_1_number_1),
                        color = colors.neutral0,
                        style = montserrat.body2,
                    )
                    Text(
                        text = stringResource(R.string.register_vault_screen_step_1_save),
                        color = colors.oxfordBlue600Main,
                        style = montserrat.subtitle2,
                        modifier = Modifier
                            .clip(shape = RoundedCornerShape(8.dp))
                            .clickOnce(onClick = onSaveVaultClick)
                            .background(color = colors.turquoise600Main)
                            .padding(
                                vertical = 4.dp,
                                horizontal = 8.dp
                            )
                    )
                    Text(
                        text = stringResource(R.string.register_vault_screen_step_1_your_vault_qr),
                        color = colors.neutral0,
                        style = montserrat.body2,
                    )
                }
                UiSpacer(size = 6.dp)
                Text(
                    text = stringResource(R.string.register_vault_screen_step_1_top_right),
                    color = colors.neutral0,
                    style = montserrat.body1,
                )
            }
        }
        UiSpacer(size = 16.dp)
        FormCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                modifier = Modifier.padding(16.dp),
                text = buildAnnotatedString {
                    append(stringResource(R.string.register_vault_screen_step_2_go_to))
                    withLink(
                        link = LinkAnnotation.Clickable(
                            tag = "Vultisig Web tag",
                            linkInteractionListener = {
                                onVultisigLinkClick()
                            },
                        ),
                    ) {
                        withStyle(
                            style = SpanStyle(
                                color = colors.turquoise600Main,
                            )
                        ) {
                            append(" ")
                            append(stringResource(R.string.register_vault_screen_step_2_vultisig_web))
                        }
                    }
                },
                color = colors.neutral0,
                style = montserrat.body2,
            )

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
        UiSpacer(weight = 1f)
    }
}

@Preview
@Composable
private fun RegisterVaultScreenPreview() {
    RegisterVaultScreen({}, {})
}

