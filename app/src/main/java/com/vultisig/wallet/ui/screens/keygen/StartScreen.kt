package com.vultisig.wallet.ui.screens.keygen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.vultisig.wallet.ui.models.keygen.StartViewModel
import com.vultisig.wallet.ui.components.v3.V3Scaffold
import com.vultisig.wallet.ui.theme.Theme
import com.vultisig.wallet.ui.utils.startScreenAnimations

private const val IS_IMPORT_SEEDPHRASE_ENABLED = true

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
        onImportSeedphraseClick = model::navigateToImportSeedphrase,
        isImportSeedphraseEnabled = IS_IMPORT_SEEDPHRASE_ENABLED,
    )
}

@Composable
private fun StartScreen(
    hasBackButton: Boolean,
    onCreateNewVaultClick: () -> Unit,
    onScanQrCodeClick: () -> Unit,
    onImportVaultClick: () -> Unit,
    onImportSeedphraseClick: () -> Unit,
    onBackClick: () -> Unit,
    isImportSeedphraseEnabled: Boolean = false,
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

    V3Scaffold(
        onBackClick = if (hasBackButton) onBackClick else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = CenterHorizontally
            ) {
                Image(
                    painter = painterResource(id = R.drawable.logo),
                    contentDescription = "vultisig",
                    modifier = Modifier
                        .width(60.dp)
                        .scale(logoScale.value)
                )
                UiSpacer(16.dp)
                Text(
                    text = stringResource(R.string.create_new_vault_screen_vultisig),
                    color = Theme.v2.colors.text.primary,
                    style = Theme.brockmann.headings.title1
                )
            }

            Column(
                horizontalAlignment = CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),

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

                VsButton(
                    label = stringResource(R.string.home_screen_scan_qr_code),
                    variant = VsButtonVariant.Secondary,
                    onClick = onScanQrCodeClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .startScreenAnimations(
                            delay = 350,
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

                if (isImportSeedphraseEnabled) {
                    VsButton(
                        label = stringResource(R.string.home_screen_import_seed_phrase),
                        variant = VsButtonVariant.Secondary,
                        onClick = onImportSeedphraseClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .startScreenAnimations(
                                delay = 550,
                            ),
                    )
                }
            }
        }
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
        onImportSeedphraseClick = {},
        onBackClick = {},
    )
}