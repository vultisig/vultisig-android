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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vultisig.wallet.R
import com.vultisig.wallet.ui.components.UiSpacer
import com.vultisig.wallet.ui.components.buttons.VsButton
import com.vultisig.wallet.ui.components.buttons.VsButtonVariant
import com.vultisig.wallet.ui.components.scaffold.VsScaffold
import com.vultisig.wallet.ui.models.keygen.StartViewModel
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.startScreenAnimations

@Composable
internal fun StartScreen(
    model: StartViewModel = hiltViewModel(),
) {
    val state by model.state.collectAsState()

    StartScreen(
        hasBackButton = state.hasBackButton,
        onBackClick = model::back,
        onCreateNewVaultClick = model::navigateToCreateVault,
        onScanQrCodeClick = model::navigateToScanQrCode,
        onImportVaultClick = model::navigateToImportVault,
    )
}

@Composable
private fun StartScreen(
    hasBackButton: Boolean,
    onCreateNewVaultClick: () -> Unit,
    onScanQrCodeClick: () -> Unit,
    onImportVaultClick: () -> Unit,
    onBackClick: () -> Unit,
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

    VsScaffold(
        onBackClick = if (hasBackButton) onBackClick else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Theme.colors.backgrounds.primary),
            horizontalAlignment = CenterHorizontally,
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
                    all = 8.dp
                )
            ) {

                VsButton(
                    label = stringResource(R.string.create_new_vault_screen_create_new_vault),
                    modifier = Modifier
                        .startScreenAnimations(
                            delay = 100,
                        )
                        .fillMaxWidth()
                        .testTag("StartScreen.createNewVault"),
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
                    variant = VsButtonVariant.Tertiary,
                    onClick = onScanQrCodeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .startScreenAnimations(
                            delay = 450,
                        ),
                )


                VsButton(
                    label = stringResource(R.string.home_screen_import_vault),
                    variant = VsButtonVariant.Tertiary,
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
            color = Theme.colors.border.light,
        )
        Text(
            text = text,
            style = Theme.brockmann.supplementary.caption,
            color = Theme.colors.text.primary
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = Theme.colors.border.light,
        )
    }
}

@Preview
@Composable
private fun StartScreenPreview() {
    StartScreen(
        hasBackButton = true,
        onCreateNewVaultClick = {},
        onScanQrCodeClick = {},
        onImportVaultClick = {},
        onBackClick = {},
    )
}