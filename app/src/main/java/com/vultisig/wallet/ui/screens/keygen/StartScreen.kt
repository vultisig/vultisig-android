package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.models.keygen.StartViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.startScreenAnimations

@Composable
internal fun StartScreen(
    model: StartViewModel = hiltViewModel()
) {
    StartScreen(
        onCreateNewVaultClick = model::navigateToCreateVault,
        onScanQrCodeClick = model::navigateToScanQrCode,
        onImportVaultClick = model::navigateToImportVault,
    )
}

@Composable
private fun StartScreen(
    onCreateNewVaultClick: () -> Unit,
    onScanQrCodeClick: () -> Unit,
    onImportVaultClick: () -> Unit
) {
    val logoScale = remember {
        Animatable(0f)
    }

    LaunchedEffect(Unit) {
        logoScale.animateTo(
            1f,
            animationSpec = tween(
                durationMillis = 200,
                delayMillis = 200
            ),
        )
    }

    Scaffold(
        containerColor = Theme.colors.backgrounds.primary,
    ) {
        Column(
            modifier = Modifier
                .padding(it)
                .fillMaxSize()
                .background(Theme.colors.backgrounds.primary),
            horizontalAlignment = CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = CenterHorizontally
            ) {


                Image(
                    painter = painterResource(id = R.drawable.vultisig),
                    contentDescription = "vultisig",
                    modifier = Modifier
                        .width(200.dp)
                        .scale(logoScale.value)
                )
                UiSpacer(16.dp)
                Text(
                    text = stringResource(R.string.create_new_vault_screen_vultisig),
                    color = Theme.colors.text.primary,
                    style = Theme.brockmann.headings.largeTitle
                )
            }
            Column(
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(
                    vertical = 24.dp,
                    horizontal = 25.dp
                )
            ) {

                VsButton(
                    label = stringResource(R.string.create_new_vault_screen_create_new_vault),
                    modifier = Modifier
                        .startScreenAnimations(
                            delay = 100,
                        )
                        .fillMaxWidth(),
                    onClick = onCreateNewVaultClick
                )

                SeparatorWithText(
                    text = stringResource(R.string.create_new_vault_screen_or),
                    modifier = Modifier
                        .fillMaxWidth()
                        .startScreenAnimations(
                            delay = 350,
                        )
                )

                VsButton(
                    label = stringResource(R.string.home_screen_scan_qr_code),
                    variant = VsButtonVariant.Secondary,
                    onClick = onScanQrCodeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .startScreenAnimations(
                            delay = 450,
                        ),
                )


                VsButton(
                    label = stringResource(R.string.home_screen_import_vault),
                    variant = VsButtonVariant.Secondary,
                    onClick = onImportVaultClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .startScreenAnimations(
                            delay = 450,
                        ),
                )
            }
        }
    }
}

@Composable
private fun SeparatorWithText(
    text: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Theme.colors.borders.light,
        )
        Text(
            text = text,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.primary
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Theme.colors.borders.light,
        )
    }
}


@Preview
@Composable
private fun StartScreenPreview() {
    StartScreen(
        onCreateNewVaultClick = {},
        onScanQrCodeClick = {},
        onImportVaultClick = {}
    )
}